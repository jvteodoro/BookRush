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

container="$(docker run -d --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=validation-only \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=validation-only \
  -v "$repo_dir/infrastructure/keycloak/bookrush-realm.json:/opt/keycloak/data/import/bookrush-realm.json:ro" \
  -v "$repo_dir/infrastructure/keycloak/bookrush-platform-realm.json:/opt/keycloak/data/import/bookrush-platform-realm.json:ro" \
  -p 127.0.0.1::8080 quay.io/keycloak/keycloak:25.0 start-dev --http-port=8080 --import-realm)"
port="$(docker port "$container" 8080/tcp | awk -F: '{print $NF}')"
base="http://127.0.0.1:$port"
for _ in $(seq 1 60); do
  if curl -fsS "$base/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then break; fi
  sleep 1
done
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
