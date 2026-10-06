#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable validation of the two declarative realms. It deliberately creates
# no users and never touches the persistent Compose keycloak_data volume.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
name="bookrush-keycloak-realms-$RANDOM"
container=""
cleanup() {
  if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

container="$(docker create --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=validation-only \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=validation-only \
  -p 127.0.0.1::8080 \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm')"
docker cp "$repo_dir/infrastructure/keycloak/bookrush-realm.json" \
  "$container:/opt/keycloak/bookrush-realm.json"
docker cp "$repo_dir/infrastructure/keycloak/bookrush-platform-realm.json" \
  "$container:/opt/keycloak/bookrush-platform-realm.json"
docker start "$container" >/dev/null
container_ip="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$container")"
test -n "$container_ip"
base="http://$container_ip:8080"
ready=false
for _ in $(seq 1 180); do
  if curl -fsS "$base/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then break; fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
    echo 'Keycloak descartável encerrou antes de disponibilizar o discovery OIDC' >&2
    docker logs "$container" >&2 || true
    exit 1
  fi
  sleep 1
done
if curl -fsS "$base/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then
  ready=true
fi
if [[ "$ready" != true ]]; then
  echo 'Timeout aguardando o discovery OIDC do Keycloak descartável' >&2
  docker logs "$container" >&2 || true
  exit 1
fi
curl -fsS "$base/realms/bookrush/.well-known/openid-configuration" >/dev/null
curl -fsS "$base/realms/bookrush-platform/.well-known/openid-configuration" >/dev/null
logs="$(docker logs "$container" 2>&1)"
grep -q "Realm 'bookrush' imported" <<<"$logs" &&
grep -q "Realm 'bookrush-platform' imported" <<<"$logs" || {
  echo 'Keycloak não registrou a importação dos realms declarativos' >&2
  printf '%s\n' "$logs" >&2
  exit 1
}
echo 'Keycloak declarative realms and OIDC discovery: OK'
