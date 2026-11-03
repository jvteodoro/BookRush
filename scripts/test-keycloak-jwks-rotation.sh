#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable proof; never connects to the persistent Compose Keycloak.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
name="bookrush-keycloak-jwks-$RANDOM"
container=""
probe_container=""
cleanup() {
  [[ -z "$probe_container" ]] || docker rm -f "$probe_container" >/dev/null 2>&1 || true
  [[ -z "$container" ]] || docker rm -f "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

container="$(docker create --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=validation-only \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=validation-only \
  -e KC_SPI_KEY_PROVIDER__RSA_GENERATED__ROTATION_PERIOD=5 \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm')"
docker cp "$repo_dir/infrastructure/keycloak/bookrush-realm.json" "$container:/opt/keycloak/bookrush-realm.json"
docker start "$container" >/dev/null
probe_container="$(docker run -d --network "container:$container" --entrypoint /bin/sh curlimages/curl:8.10.1 -c 'sleep 300')"
probe() { docker exec "$probe_container" curl -fsS "$@"; }
for _ in $(seq 1 180); do
  if probe http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration >/dev/null; then break; fi
  [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == true ]] || { docker logs "$container" >&2 || true; exit 1; }
  sleep 1
done
config="$(probe http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration)"
jwks_uri="$(jq -er '.jwks_uri' <<<"$config")"
first_kids="$(probe "$jwks_uri" | jq -r '.keys[].kid' | sort)"
test -n "$first_kids"
# Add a higher-priority generated provider through Keycloak's supported admin
# API. This makes the rotation deterministic instead of waiting on a server
# scheduler whose interval is intentionally not part of the application
# contract.
docker exec "$container" /opt/keycloak/bin/kcadm.sh config credentials \
  --config /tmp/kcadm.config --server http://127.0.0.1:8080 \
  --realm master --user admin --password validation-only >/dev/null
docker exec "$container" /opt/keycloak/bin/kcadm.sh create components \
  --config /tmp/kcadm.config -r bookrush \
  -s name=codex-rotation-test -s providerId=rsa-generated \
  -s providerType=org.keycloak.keys.KeyProvider \
  -s 'config.priority=["200"]' -s 'config.keySize=["2048"]' >/dev/null
sleep 2
second_kids="$(probe "$jwks_uri" | jq -r '.keys[].kid' | sort)"
test -n "$second_kids"
if [[ "$first_kids" == "$second_kids" ]]; then
  echo 'Keycloak JWKS não alterou o kid dentro da janela de rotação' >&2
  exit 1
fi
echo 'Keycloak JWKS rotation: kid changed and JWKS remained available (disposable)'
