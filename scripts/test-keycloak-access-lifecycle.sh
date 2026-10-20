#!/usr/bin/env bash
set -Eeuo pipefail

# Disposable proof of the administrative access lifecycle. It never uses the
# project Compose network, persistent volumes, production credentials, or a
# real user.
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }
json_access_token() {
  sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"\\]*\)".*/\1/p'
}
name="bookrush-keycloak-access-$RANDOM"
probe="bookrush-keycloak-access-probe-$RANDOM"
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
admin_password='disposable-admin-password'
user_name="codex-lifecycle-$RANDOM"
token_user_name="codex-token-$RANDOM"
client_id="codex-lifecycle-$RANDOM"
user_id=''
token_user_id=''
client_uuid=''
group_id=''
cleanup() {
  local rc=$?
  if [[ -n "$user_id" ]]; then docker exec "$name" /opt/keycloak/bin/kcadm.sh delete "users/$user_id" -r bookrush-platform >/dev/null 2>&1 || true; fi
  if [[ -n "$token_user_id" ]]; then docker exec "$name" /opt/keycloak/bin/kcadm.sh delete "users/$token_user_id" -r bookrush-platform >/dev/null 2>&1 || true; fi
  if [[ -n "$client_uuid" ]]; then docker exec "$name" /opt/keycloak/bin/kcadm.sh delete "clients/$client_uuid" -r bookrush-platform >/dev/null 2>&1 || true; fi
  docker rm -f "$probe" >/dev/null 2>&1 || true
  docker rm -f "$name" >/dev/null 2>&1 || true
  exit "$rc"
}
trap cleanup EXIT

docker create --name "$name" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD="$admin_password" \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD="$admin_password" \
  --entrypoint /bin/sh quay.io/keycloak/keycloak:25.0 \
  -c 'mkdir -p /opt/keycloak/data/import && cp /opt/keycloak/bookrush-platform-realm.json /opt/keycloak/data/import/ && exec /opt/keycloak/bin/kc.sh start-dev --http-port=8080 --import-realm' >/dev/null
docker cp "$repo_dir/infrastructure/keycloak/bookrush-platform-realm.json" "$name:/opt/keycloak/bookrush-platform-realm.json"
docker start "$name" >/dev/null
probe="$(docker run -d --network "container:$name" --entrypoint /bin/sh curlimages/curl:8.10.1 -c 'sleep 240')"
for _ in $(seq 1 120); do
  if docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/master/.well-known/openid-configuration' >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$probe" curl -fsS 'http://127.0.0.1:8080/realms/master/.well-known/openid-configuration' >/dev/null

admin() { docker exec "$name" /opt/keycloak/bin/kcadm.sh "$@"; }
admin config credentials --server http://127.0.0.1:8080 --realm master --user admin --password "$admin_password" >/dev/null

otp_policy="$(admin get realms/bookrush-platform --fields otpPolicyType,otpPolicyAlgorithm,otpPolicyDigits,otpPolicyLookAheadWindow,otpPolicyPeriod --format csv --noquotes)"
grep -q 'totp' <<<"$otp_policy"
grep -q 'HmacSHA1' <<<"$otp_policy"
grep -q '6' <<<"$otp_policy"

client_uuid="$(admin create clients -r bookrush-platform -s clientId="$client_id" -s enabled=true -s publicClient=true -s directAccessGrantsEnabled=true -s standardFlowEnabled=false -i)"
admin create users -r bookrush-platform -s username="$user_name" -s enabled=true -s emailVerified=true >/dev/null
user_id="$(admin get users -r bookrush-platform -q username="$user_name" --fields id --format csv --noquotes | awk 'NR == 1 { first = $0 } END { print first }')"
test -n "$user_id"
group_id="$(admin get groups -r bookrush-platform -q search=operators --fields id --format csv --noquotes | awk 'NR == 1 { first = $0 } END { print first }')"
test -n "$group_id"
admin update "users/$user_id/groups/$group_id" -r bookrush-platform >/dev/null
admin update "users/$user_id" -r bookrush-platform \
  -s 'requiredActions=["CONFIGURE_TOTP"]' \
  -s 'credentials=[{"type":"password","value":"disposable-user-password","temporary":false}]' >/dev/null
admin get "users/$user_id/groups" -r bookrush-platform --format csv --noquotes | grep -q 'operators'
admin get "users/$user_id" -r bookrush-platform --fields requiredActions --format csv --noquotes | grep -q 'CONFIGURE_TOTP'

token_url='http://127.0.0.1:8080/realms/bookrush-platform/protocol/openid-connect/token'
set +e
mfa_response="$(docker exec "$probe" curl -sS -w '\n%{http_code}' -X POST "$token_url" \
  -d grant_type=password -d client_id="$client_id" -d username="$user_name" -d password='disposable-user-password')"
set -e
grep -Eq '^(400|401)$' <<<"$mfa_response"
grep -qi 'not fully set up\|required action' <<<"$mfa_response"

admin create users -r bookrush-platform -s username="$token_user_name" -s firstName=Codex -s lastName=Lifecycle -s enabled=true -s email='codex-token@bookrush.test' -s emailVerified=true >/dev/null
token_user_id="$(admin get users -r bookrush-platform -q username="$token_user_name" --fields id --format csv --noquotes | awk 'NR == 1 { first = $0 } END { print first }')"
test -n "$token_user_id"
for _ in 1 2; do
  docker run --rm -i --network "container:$name" \
    -e KEYCLOAK_URL=http://127.0.0.1:8080 -e KEYCLOAK_USER=admin \
    -e KEYCLOAK_PASSWORD="$admin_password" -e ADMIN_WEB_ADMIN_USERNAME="$token_user_name" \
    python:3.12-slim python3 - < "$repo_dir/infrastructure/keycloak/grant-admin-console-access.py"
done
admin get "users/$token_user_id/groups" -r bookrush-platform --format csv --noquotes \
  | grep -q 'platform-admins'
admin update "users/$token_user_id" -r bookrush-platform -s firstName=Codex -s lastName=Lifecycle -s enabled=true -s email='codex-token@bookrush.test' -s emailVerified=true -s 'requiredActions=[]' >/dev/null
token_user_state="$(admin get "users/$token_user_id" -r bookrush-platform --fields enabled,emailVerified,requiredActions --format csv --noquotes)"
grep -q 'true' <<<"$token_user_state"
! grep -q 'CONFIGURE_TOTP\|VERIFY_EMAIL\|UPDATE_PASSWORD' <<<"$token_user_state"
admin set-password -r bookrush-platform --userid "$token_user_id" --new-password 'disposable-user-password' >/dev/null
token_response="$(docker exec "$probe" curl -sS -w '\n%{http_code}' -X POST "$token_url" \
  -d grant_type=password -d client_id="$client_id" -d username="$token_user_name" -d password='disposable-user-password')"
grep -q '^200$' <<<"$token_response"
grep -q 'access_token' <<<"$token_response"
access_token="$(sed -n '1p' <<<"$token_response" | json_access_token)"
unset access_token

admin_web_client_id="$(admin get clients -r bookrush-platform -q clientId=labsoft-admin-web --fields id --format csv --noquotes)"
test -n "$admin_web_client_id"
admin update "clients/$admin_web_client_id" -r bookrush-platform -s directAccessGrantsEnabled=true >/dev/null
admin_web_token_response="$(docker exec "$probe" curl -fsS -X POST "$token_url" \
  -d grant_type=password -d client_id=labsoft-admin-web \
  -d username="$token_user_name" -d password='disposable-user-password')"
access_token="$(json_access_token <<<"$admin_web_token_response")"
test -n "$access_token"
docker exec -e ACCESS_TOKEN="$access_token" "$probe" /bin/sh -c '
  set -eu
  payload="${ACCESS_TOKEN#*.}"
  payload="${payload%%.*}"
  payload="$(printf "%s" "$payload" | tr "_-" "/+" | base64 -d 2>/dev/null)"
  printf "%s" "$payload" | grep -q '"sub"'
  printf "%s" "$payload" | grep -q 'platform-admins'
  printf "%s" "$payload" | grep -q 'bookrush-ingestion'
'
unset access_token

admin update "users/$token_user_id" -r bookrush-platform -s enabled=false >/dev/null
set +e
disabled_response="$(docker exec "$probe" curl -sS -w '\n%{http_code}' -X POST "$token_url" \
  -d grant_type=password -d client_id="$client_id" -d username="$token_user_name" -d password='disposable-user-password')"
set -e
grep -q '^400$' <<<"$disabled_response"
grep -qi 'disabled\|invalid user credentials' <<<"$disabled_response"
echo 'Keycloak access lifecycle: OTP requirement, privileged group and offboarding revocation OK (ambiente descartável)'
