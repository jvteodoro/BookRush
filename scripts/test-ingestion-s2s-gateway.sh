#!/usr/bin/env bash
set -Eeuo pipefail

: "${INGESTION_BASE_URL:?INGESTION_BASE_URL is required}"
: "${INGESTION_CLIENT_SECRET:?INGESTION_CLIENT_SECRET is required}"
client_id="${INGESTION_CLIENT_ID:-bookrush-ingestion-admin}"
token_url="${INGESTION_TOKEN_URL:-https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform/protocol/openid-connect/token}"
external_id="${INGESTION_EXTERNAL_ID:-1342}"
base="${INGESTION_BASE_URL%/}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
for command in curl jq python3; do command -v "$command" >/dev/null || { echo "s2s-gateway: comando ausente: $command" >&2; exit 2; }; done
token_response="$(curl --fail-with-body --silent --show-error --connect-timeout 10 --max-time 30 -X POST "$token_url" -d grant_type=client_credentials --data-urlencode "client_id=$client_id" --data-urlencode "client_secret=$INGESTION_CLIENT_SECRET")"
token="$(jq -er '.access_token' <<<"$token_response")"
python3 - "$token" <<'PY'
import base64, json, sys
part = sys.argv[1].split('.')[1]
claims = json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))
assert 'bookrush-ingestion' in claims.get('aud', []), claims.get('aud')
assert claims.get('exp', 0) > claims.get('iat', 0), 'token sem janela válida'
PY
request="$(jq -cn --arg id "$external_id" '{source:"GUTENBERG",externalIds:[$id],languages:[],maxItems:1,dryRun:true,processAssets:false,reprocess:false}')"
key="s2s-validation-$(date +%s)-$$"
status="$(curl --silent --show-error --connect-timeout 10 --max-time 30 -o "$tmp_dir/body" -w '%{http_code}' -X POST "$base/api/admin/v1/ingestion/run" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" --data "$request")"
case "$status" in
  200|202) echo "Ingestion S2S gateway: token audience and dry-run accepted (HTTP $status)" ;;
  *) echo "Ingestion S2S gateway: expected HTTP 200/202, received $status" >&2; cat "$tmp_dir/body" >&2; exit 1 ;;
esac
