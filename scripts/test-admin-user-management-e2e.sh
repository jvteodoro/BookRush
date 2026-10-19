#!/usr/bin/env bash
set -Eeuo pipefail

# End-to-end proof of platform token -> admin-service -> product-realm users.
# All containers, credentials and data are disposable.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-admin-e2e.XXXXXX")"
suffix="$RANDOM-$$"
network="bookrush-admin-e2e-$suffix"
keycloak="bookrush-admin-e2e-keycloak-$suffix"
postgres="bookrush-admin-e2e-postgres-$suffix"
service="bookrush-admin-e2e-service-$suffix"
web="bookrush-admin-e2e-web-$suffix"
probe="bookrush-admin-e2e-probe-$suffix"
admin_password='disposable-admin-password'
product_secret='disposable-product-client-secret'
platform_secret='disposable-platform-client-secret'
image="bookrush/admin-service-e2e:$suffix"
web_image="bookrush/admin-web-e2e:$suffix"
cleanup() {
  local rc=$?
  docker rm -f "$probe" "$web" "$service" "$keycloak" "$postgres" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$rc"
}
trap cleanup EXIT

KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" \
INGESTION_ADMIN_CLIENT_SECRET="$platform_secret" \
  python3 "$repo_dir/infrastructure/keycloak/render-config.py" \
  "$repo_dir/infrastructure/keycloak" "$work_dir/rendered"

docker network create "$network" >/dev/null
docker run -d --name "$postgres" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=bookrush -e POSTGRES_USER=bookrush -e POSTGRES_PASSWORD=bookrush \
  postgres:16-alpine >/dev/null
# Reproduce the Compose deployment order that exposed the production race:
# admin-web must remain running even while admin-service has no DNS record yet.
docker build -q -t "$web_image" "$repo_dir/admin-web" >/dev/null
docker run -d --name "$web" --network "$network" --network-alias admin-web \
  "$web_image" >/dev/null
sleep 1
[[ "$(docker inspect --format '{{.State.Running}}' "$web")" == true ]] || {
  docker logs "$web" >&2
  echo 'Admin web encerrou antes de o admin-service entrar na rede.' >&2
  exit 1
}
docker create --name "$keycloak" --network "$network" --network-alias keycloak \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /tmp/realms/*.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
docker cp "$work_dir/rendered" "$keycloak:/tmp/realms"
docker start "$keycloak" >/dev/null
probe="$(docker run -d --name "$probe" --network "$network" curlimages/curl:8.10.1 tail -f /dev/null)"
for _ in $(seq 1 120); do
  docker exec "$probe" curl -fsS http://keycloak:8080/realms/bookrush-platform/.well-known/openid-configuration >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS http://keycloak:8080/realms/bookrush/.well-known/openid-configuration >/dev/null

# Match hardened/persistent installations where assigned service-account roles
# are not automatically included in the token.
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://127.0.0.1:8080 --realm master --user admin \
  --password "$admin_password" >/dev/null
client_uuid="$(docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh get clients -r bookrush \
  -q clientId=bookrush-admin-service --fields id --format csv --noquotes)"
docker exec "$keycloak" /opt/keycloak/bin/kcadm.sh update "clients/$client_uuid" \
  -r bookrush -s fullScopeAllowed=false >/dev/null

# Exercise twice the same role repair used immediately before deployment,
# proving that it is safe against an already-reconciled persistent realm.
for _ in 1 2; do
  docker run --rm -i --network "$network" \
    -e KEYCLOAK_URL=http://keycloak:8080 -e KEYCLOAK_USER=admin \
    -e KEYCLOAK_PASSWORD="$admin_password" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-user-management-roles.py"
done

docker build -q -t "$image" -f "$repo_dir/services/admin-service/Dockerfile" "$repo_dir" >/dev/null
for _ in $(seq 1 60); do
  docker exec "$postgres" pg_isready -U bookrush -d bookrush >/dev/null 2>&1 && break
  sleep 1
done
docker run -d --name "$service" --network "$network" --network-alias admin-service \
  -e DB_HOST=postgres -e DB_NAME=bookrush -e DB_USERNAME=bookrush -e DB_PASSWORD=bookrush \
  -e BOOKRUSH_SECURITY_ENABLED=true \
  -e BOOKRUSH_SECURITY_ISSUER=http://keycloak:8080/realms/bookrush-platform \
  -e BOOKRUSH_SECURITY_JWK_SET_URI=http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/certs \
  -e BOOKRUSH_SECURITY_AUDIENCE=bookrush-ingestion \
  -e KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token \
  -e KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush \
  -e KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET="$product_secret" "$image" >/dev/null
for _ in $(seq 1 90); do
  docker exec "$probe" curl -fsS http://admin-service:8100/actuator/health >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$probe" curl -fsS http://admin-service:8100/actuator/health >/dev/null

token="$(docker exec "$probe" curl -fsS -X POST \
  http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=bookrush-ingestion-admin \
  --data-urlencode "client_secret=$platform_secret" | jq -er '.access_token')"
status=000
for _ in $(seq 1 30); do
  status="$(docker exec "$probe" curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    'http://admin-web/api/v1/admin/users?page=0&size=1' 2>/dev/null || true)"
  [[ "$status" == 200 ]] && break
  sleep 1
done
if [[ "$status" != 200 ]]; then
  docker logs --tail=160 "$service" >&2
  echo "Admin user management E2E retornou HTTP $status." >&2
  exit 1
fi
echo 'Admin user management E2E: platform token -> service -> product realm OK'
