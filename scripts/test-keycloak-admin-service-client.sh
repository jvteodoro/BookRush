#!/usr/bin/env bash
set -Eeuo pipefail

env_file="${1:-.env}"
[[ -f "$env_file" ]] || { echo "Arquivo ausente: $env_file" >&2; exit 2; }
command -v curl >/dev/null || { echo 'curl ausente.' >&2; exit 2; }
command -v jq >/dev/null || { echo 'jq ausente.' >&2; exit 2; }
set -a
. "$env_file"
set +a
: "${KEYCLOAK_ADMIN_CLIENT_SECRET:?KEYCLOAK_ADMIN_CLIENT_SECRET ausente}"
base="http://127.0.0.1:${BOOKRUSH_KEYCLOAK_PORT:-18088}"
client="${KEYCLOAK_ADMIN_CLIENT_ID:-bookrush-admin-service}"
realm="${KEYCLOAK_USER_MANAGEMENT_REALM:-bookrush}"
token="$(curl -fsS -X POST "$base/realms/$realm/protocol/openid-connect/token" \
  -d grant_type=client_credentials --data-urlencode "client_id=$client" \
  --data-urlencode "client_secret=$KEYCLOAK_ADMIN_CLIENT_SECRET" | jq -er '.access_token')"
roles="$(python3 - "$token" <<'PY'
import base64, json, sys
payload = sys.argv[1].split('.')[1]
payload += '=' * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
roles = claims.get('resource_access', {}).get('realm-management', {}).get('roles', [])
print(','.join(sorted(roles)))
PY
)"
[[ "$roles" == *manage-users* && "$roles" == *query-users* && "$roles" == *view-users* ]] || {
  echo 'Keycloak admin-service: roles query-users/view-users/manage-users ausentes.' >&2
  exit 1
}
status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" \
  "$base/admin/realms/$realm/users?max=1")"
[[ "$status" == 200 ]] || { echo "Keycloak admin-service: API de usuários retornou HTTP $status." >&2; exit 1; }
echo 'Keycloak admin-service client: token, roles e API de usuários OK'
