#!/usr/bin/env bash
set -Eeuo pipefail

# Proves the product-realm service account can list and manage disposable users.
# It never touches the project network, persistent volumes, or production data.
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-user-management.XXXXXX")"
name="bookrush-user-management-$RANDOM"
probe="bookrush-user-management-probe-$RANDOM"
admin_password='disposable-admin-password'
client_secret='disposable-client-secret'
target_username="managed-reader-$RANDOM"
cleanup() {
  local rc=$?
  docker rm -f "$probe" "$name" >/dev/null 2>&1 || true
  rm -rf -- "$work_dir"
  exit "$rc"
}
trap cleanup EXIT

docker run --rm --user "$(id -u):$(id -g)" \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET="$client_secret" \
  -v "$repo_dir:/repo:ro" -v "$work_dir:/out" \
  python:3.12-slim python3 /repo/infrastructure/keycloak/render-config.py \
  /repo/infrastructure/keycloak /out/rendered
docker create --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /tmp/bookrush-realm.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
docker cp "$work_dir/rendered/bookrush-realm.json" "$name:/tmp/bookrush-realm.json"
docker start "$name" >/dev/null
probe="$(docker run -d --network "container:$name" --entrypoint /bin/sh curlimages/curl:8.10.1 -c 'sleep 240')"
for _ in $(seq 1 120); do
  docker exec "$probe" curl -fsS http://127.0.0.1:8080/realms/bookrush/.well-known/openid-configuration >/dev/null 2>&1 && break
  sleep 1
done

admin() { docker exec "$name" /opt/keycloak/bin/kcadm.sh "$@"; }
admin config credentials --server http://127.0.0.1:8080 --realm master --user admin --password "$admin_password" >/dev/null
client_uuid="$(admin get clients -r bookrush -q clientId=bookrush-admin-service --fields id --format csv --noquotes)"
service_user="$(admin get "clients/$client_uuid/service-account-user" -r bookrush --fields id --format csv --noquotes)"
realm_management="$(admin get clients -r bookrush -q clientId=realm-management --fields id --format csv --noquotes)"
admin add-roles -r bookrush --uid "$service_user" \
  --cclientid realm-management --rolename query-users --rolename manage-users >/dev/null
admin add-roles -r bookrush --uid "$service_user" \
  --cclientid realm-management --rolename view-users >/dev/null
role_state="$(admin get "users/$service_user/role-mappings/clients/$realm_management" -r bookrush --fields name --format csv --noquotes)"
grep -q query-users <<<"$role_state"
grep -q manage-users <<<"$role_state"
grep -q view-users <<<"$role_state"
admin create users -r bookrush -s username="$target_username" -s enabled=true -s email="$target_username@bookrush.test" >/dev/null
target_id="$(admin get users -r bookrush -q username="$target_username" --fields id --format csv --noquotes)"
test -n "$service_user" && test -n "$realm_management" && test -n "$target_id"

resolved_secret="$(docker run --rm -i --network "container:$name" \
  -e KEYCLOAK_URL=http://127.0.0.1:8080 \
  -e KEYCLOAK_REALM=bookrush \
  -e KEYCLOAK_CLIENT_ID=bookrush-admin-service \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD="$admin_password" \
  python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/read-client-secret.py")"
test "$resolved_secret" = "$client_secret"
echo 'Keycloak user management: protected client secret resolution OK'

token_response="$(docker exec "$probe" curl -fsS -X POST http://127.0.0.1:8080/realms/bookrush/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=bookrush-admin-service \
  --data-urlencode "client_secret=$client_secret")"
token="$(sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"\\]*\)".*/\1/p' <<<"$token_response")"
test -n "$token"
auth=(-H "Authorization: Bearer $token")
docker exec -e ACCESS_TOKEN="$token" "$probe" /bin/sh -c '
  set -eu
  payload="${ACCESS_TOKEN#*.}"
  payload="${payload%%.*}"
  payload="$(printf "%s" "$payload" | tr "_-" "/+" | base64 -d 2>/dev/null)"
  printf "%s" "$payload" | grep -q "query-users"
  printf "%s" "$payload" | grep -q "view-users"
  printf "%s" "$payload" | grep -q "manage-users"
'
echo 'Keycloak user management: service token roles OK'
docker exec "$probe" curl -fsS "${auth[@]}" \
  "http://127.0.0.1:8080/admin/realms/bookrush/users?search=$target_username" | grep -q "$target_username"
echo 'Keycloak user management: search OK'
docker exec "$probe" curl -fsS -X PUT "${auth[@]}" -H 'Content-Type: application/json' \
  -d '{"enabled":false}' "http://127.0.0.1:8080/admin/realms/bookrush/users/$target_id"
echo 'Keycloak user management: disable OK'
docker exec "$probe" curl -fsS -X POST "${auth[@]}" \
  "http://127.0.0.1:8080/admin/realms/bookrush/users/$target_id/logout"
echo 'Keycloak user management: logout OK'
admin get "users/$target_id" -r bookrush --fields enabled --format csv --noquotes | grep -q false
docker exec "$probe" curl -fsS -X PUT "${auth[@]}" -H 'Content-Type: application/json' \
  -d '{"enabled":true}' "http://127.0.0.1:8080/admin/realms/bookrush/users/$target_id"
admin get "users/$target_id" -r bookrush --fields enabled --format csv --noquotes | grep -q true
echo 'Keycloak user management: search, disable, logout and enable OK (ambiente descartável)'
