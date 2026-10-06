#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable validation of the two declarative realms. It deliberately creates
# no users and never touches the persistent Compose keycloak_data volume.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
name="bookrush-keycloak-realms-$RANDOM"
container=""
probe_container=""
cleanup() {
  if [[ -n "$probe_container" ]]; then docker rm -f "$probe_container" >/dev/null 2>&1 || true; fi
  if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

container="$(docker create --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=validation-only \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=validation-only \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm')"
docker cp "$repo_dir/infrastructure/keycloak/bookrush-realm.json" \
  "$container:/opt/keycloak/bookrush-realm.json"
docker cp "$repo_dir/infrastructure/keycloak/bookrush-platform-realm.json" \
  "$container:/opt/keycloak/bookrush-platform-realm.json"
docker start "$container" >/dev/null
probe_container="$(docker run -d --network "container:$container" \
  --entrypoint /bin/sh curlimages/curl:8.10.1 -c 'sleep 300')"
probe() {
  docker exec "$probe_container" curl -fsS "$1" >/dev/null 2>&1
}
ready=false
for _ in $(seq 1 180); do
  if probe 'http://127.0.0.1:8080/realms/master/.well-known/openid-configuration'; then break; fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
    echo 'Keycloak descartável encerrou antes de disponibilizar o discovery OIDC' >&2
    docker logs "$container" >&2 || true
    exit 1
  fi
  sleep 1
done
if probe 'http://127.0.0.1:8080/realms/master/.well-known/openid-configuration'; then
  ready=true
fi
if [[ "$ready" != true ]]; then
  echo 'Timeout aguardando o discovery OIDC do Keycloak descartável' >&2
  docker logs "$container" >&2 || true
  exit 1
fi
probe 'http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration'
probe 'http://127.0.0.1:8080/realms/bookrush-platform/.well-known/openid-configuration'
logs="$(docker logs "$container" 2>&1)"
grep -q "Realm 'bookrush' imported" <<<"$logs" &&
grep -q "Realm 'bookrush-platform' imported" <<<"$logs" || {
  echo 'Keycloak não registrou a importação dos realms declarativos' >&2
  printf '%s\n' "$logs" >&2
  exit 1
}
echo 'Keycloak declarative realms and OIDC discovery: OK'
