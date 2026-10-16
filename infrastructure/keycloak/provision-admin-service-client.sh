#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$repo_dir/.env"
while (($#)); do
  case "$1" in
    --env-file) env_file="$(readlink -f "$2")"; shift 2;;
    -h|--help) echo 'Uso: infrastructure/keycloak/provision-admin-service-client.sh [--env-file FILE]'; exit 0;;
    *) echo "Opção desconhecida: $1" >&2; exit 2;;
  esac
done

[[ -f "$env_file" ]] || { echo "Arquivo ausente: $env_file" >&2; exit 2; }
command -v curl >/dev/null || { echo 'curl ausente.' >&2; exit 2; }
command -v jq >/dev/null || { echo 'jq ausente.' >&2; exit 2; }
command -v openssl >/dev/null || { echo 'openssl ausente.' >&2; exit 2; }

set -a
. "$env_file"
set +a
: "${KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD:?KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD ausente}"
admin_user="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-${KEYCLOAK_ADMIN:-}}"
: "${admin_user:?KEYCLOAK_CONFIG_CLI_ADMIN_USER ou KEYCLOAK_ADMIN ausente}"
port="${BOOKRUSH_KEYCLOAK_PORT:-18088}"
base="${KEYCLOAK_ADMIN_BASE:-http://127.0.0.1:${port}/admin/realms/bookrush-platform}"
base="${base%/}"
server="${base%/admin/realms/bookrush-platform}"
token_uri="${KEYCLOAK_ADMIN_TOKEN_URI:-$server/realms/bookrush-platform/protocol/openid-connect/token}"
token="$(curl -fsS -X POST "${server}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "username=${admin_user}" \
  --data-urlencode "password=${KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD}" \
  --data-urlencode grant_type=password --data-urlencode client_id=admin-cli | jq -er '.access_token')"
auth=(-H "Authorization: Bearer $token")
client_name="${KEYCLOAK_ADMIN_CLIENT_ID:-bookrush-admin-service}"
client_json="$(curl -fsS "${base}/clients?clientId=${client_name}" "${auth[@]}")"
client_id="$(jq -er '.[0].id' <<<"$client_json")"
secret="$(openssl rand -hex 32)"
updated="$(jq --arg secret "$secret" '.[0] | .secret=$secret | .clientAuthenticatorType="client-secret" | .serviceAccountsEnabled=true | .enabled=true' <<<"$client_json")"
curl -fsS -X PUT "${base}/clients/${client_id}" "${auth[@]}" \
  -H 'Content-Type: application/json' -d "$updated" >/dev/null

tmp="$(mktemp "${env_file}.XXXXXX")"
chmod 600 "$tmp"
trap 'rm -f -- "$tmp"' EXIT
awk -v key="KEYCLOAK_ADMIN_CLIENT_SECRET" -v value="$secret" '
  BEGIN { replaced=0 }
  $0 ~ "^" key "=" { print key "=" value; replaced=1; next }
  { print }
  END { if (!replaced) print key "=" value }
' "$env_file" >"$tmp"
mv -f -- "$tmp" "$env_file"
unset secret token updated client_json
echo "Cliente ${client_name} provisionado e segredo persistido em ${env_file}."
