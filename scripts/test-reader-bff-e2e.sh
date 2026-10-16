#!/usr/bin/env bash
set -Eeuo pipefail

: "${E2E_BASE_URL:?E2E_BASE_URL is required}"
: "${E2E_OIDC_USERNAME:?E2E_OIDC_USERNAME is required}"
: "${E2E_OIDC_PASSWORD:?E2E_OIDC_PASSWORD is required}"
: "${E2E_BOOK_ID:?E2E_BOOK_ID is required}"

base_url="${E2E_BASE_URL%/}"
authority="${E2E_OIDC_AUTHORITY:-https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush}"
client_id="${E2E_OIDC_CLIENT_ID:-bookrush-web}"
redirect_uri="${E2E_OIDC_REDIRECT_URI:-$base_url/auth/callback}"
origin="${E2E_ORIGIN:-$base_url}"
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

for command in curl jq openssl python3; do
  command -v "$command" >/dev/null || { echo "reader-bff-e2e: comando ausente: $command" >&2; exit 2; }
done

urlencode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote
print(quote(sys.argv[1], safe=""))
PY
}

pkce_challenge() {
  VERIFIER="$1" python3 - <<'PY'
import base64, hashlib, os
value = os.environ["VERIFIER"].encode()
print(base64.urlsafe_b64encode(hashlib.sha256(value).digest()).decode().rstrip("="))
PY
}

status_without_body() {
  local output="$1"; shift
  curl -sS -o "$output" -w '%{http_code}' "$@"
}

assert_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "reader-bff-e2e: $label esperado HTTP $expected, recebido $actual" >&2
    exit 1
  fi
  printf '%s=%s\n' "$label" "$actual"
}

echo '[1/5] rejeição sem autenticação e com token inválido'
status=$(status_without_body "$tmp_dir/no-token" "$base_url/api/v1/reader/feed?size=1")
assert_status unauthenticated_feed 401 "$status"
status=$(status_without_body "$tmp_dir/invalid-token" "$base_url/api/v1/reader/feed?size=1" \
  -H 'Authorization: Bearer invalid.reader.bff.token')
assert_status invalid_token_feed 401 "$status"

echo '[2/5] login OIDC Authorization Code + PKCE'
cookie="$tmp_dir/cookie"
auth_html="$tmp_dir/auth.html"
post_headers="$tmp_dir/post.headers"
post_body="$tmp_dir/post.body"
required_headers="$tmp_dir/required.headers"
required_body="$tmp_dir/required.body"
verifier=$(openssl rand -base64 64 | tr '+/' '-_' | tr -d '=\n' | cut -c1-64)
challenge=$(pkce_challenge "$verifier")
state=$(openssl rand -hex 24)
nonce=$(openssl rand -hex 24)
auth_url="$authority/protocol/openid-connect/auth?client_id=$(urlencode "$client_id")&redirect_uri=$(urlencode "$redirect_uri")&response_type=code&scope=$(urlencode 'openid profile email bookrush.catalog.read')&state=$state&nonce=$nonce&code_challenge=$challenge&code_challenge_method=S256&response_mode=query"
curl -fsS -L -c "$cookie" -b "$cookie" "$auth_url" -o "$auth_html"
action=$(python3 - "$auth_html" <<'PY'
import html, re, sys
text = open(sys.argv[1], encoding="utf-8").read()
match = re.search(r'<form[^>]+action="([^"]+)"', text)
if not match:
    raise SystemExit("OIDC login form not found")
print(html.unescape(match.group(1)))
PY
)
post_status=$(curl -fsS -D "$post_headers" -o "$post_body" -w '%{http_code}' -c "$cookie" -b "$cookie" "$action" \
  --data-urlencode "username=$E2E_OIDC_USERNAME" \
  --data-urlencode "password=$E2E_OIDC_PASSWORD" \
  --data-urlencode 'credentialId=' \
  --data-urlencode 'login=Entrar')
callback=$(awk 'tolower($0) ~ /^location:/{sub(/\r$/,""); sub(/^[^:]*:[[:space:]]*/,""); print; exit}' "$post_headers")
if [[ -z "$callback" ]]; then
  redirect_path=$(python3 - "$post_headers" <<'PY'
import sys
from urllib.parse import urlsplit
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    if line.lower().startswith("location:"):
        print(urlsplit(line.split(":", 1)[1].strip()).path)
        break
PY
  )
  redirect_query_keys=$(python3 - "$post_headers" <<'PY'
import sys
from urllib.parse import parse_qs, urlsplit
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    if line.lower().startswith("location:"):
        print(",".join(sorted(parse_qs(urlsplit(line.split(":", 1)[1].strip()).query).keys())))
        break
PY
  )
  redirect_error=$(python3 - "$post_headers" <<'PY'
import sys
from urllib.parse import parse_qs, urlsplit
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    if line.lower().startswith("location:"):
        print(parse_qs(urlsplit(line.split(":", 1)[1].strip()).query).get("error", [""])[0])
        break
PY
  )
  if grep -qiE 'invalid username|invalid password|credenciais inválidas' "$post_body"; then
    echo 'reader-bff-e2e: Keycloak rejeitou username/password da credential' >&2
  else
    echo "reader-bff-e2e: OIDC não retornou callback (login HTTP $post_status, redirect=${redirect_path:-none}, query_keys=${redirect_query_keys:-none}, error=${redirect_error:-none})" >&2
  fi
  exit 1
fi
if [[ "$callback" != *'code='* ]]; then
  required_action=$(curl -fsS -c "$cookie" -b "$cookie" "$callback" -o "$required_body" >/dev/null; python3 - "$required_body" <<'PY'
import html, re, sys
text = open(sys.argv[1], encoding="utf-8").read()
match = re.search(r'<form[^>]+action="([^"]+)"', text)
if not match:
    raise SystemExit("OIDC required action form not found")
print(html.unescape(match.group(1)))
PY
  )
  grep -q 'name="email"' "$required_body" || {
    echo 'reader-bff-e2e: required action OIDC não suportada automaticamente' >&2
    exit 1
  }
  curl -fsS -D "$required_headers" -o /dev/null -c "$cookie" -b "$cookie" "$required_action" \
    --data-urlencode "email=${E2E_OIDC_EMAIL:-$E2E_OIDC_USERNAME@bookrush.invalid}" \
    --data-urlencode 'firstName=Codex' \
    --data-urlencode 'lastName=Product Agent'
  callback=$(awk 'tolower($0) ~ /^location:/{sub(/\r$/,""); sub(/^[^:]*:[[:space:]]*/,""); print; exit}' "$required_headers")
fi
code_and_state=$(python3 - "$callback" <<'PY'
import sys
from urllib.parse import parse_qs, urlsplit
query = parse_qs(urlsplit(sys.argv[1]).query)
print(query.get("code", [""])[0])
print(query.get("state", [""])[0])
PY
)
code=$(sed -n '1p' <<<"$code_and_state")
returned_state=$(sed -n '2p' <<<"$code_and_state")
test -n "$code" || { echo 'reader-bff-e2e: callback sem authorization code' >&2; exit 1; }
[[ "$returned_state" == "$state" ]] || { echo 'reader-bff-e2e: state OIDC não corresponde' >&2; exit 1; }
token=$(curl -fsS -X POST "$authority/protocol/openid-connect/token" \
  --data-urlencode grant_type=authorization_code \
  --data-urlencode "client_id=$client_id" \
  --data-urlencode "redirect_uri=$redirect_uri" \
  --data-urlencode "code=$code" \
  --data-urlencode "code_verifier=$verifier")
access_token=$(jq -er '.access_token' <<<"$token")

python3 - "$access_token" "$authority" <<'PY'
import base64, json, sys, time
token, expected_issuer = sys.argv[1:]
payload = token.split('.')[1]
payload += '=' * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
if claims.get('iss') != expected_issuer:
    raise SystemExit('reader-bff-e2e: issuer inesperado')
aud = claims.get('aud', [])
if isinstance(aud, str):
    aud = [aud]
if 'account' not in aud and 'bookrush-catalog-admin' not in aud:
    raise SystemExit('reader-bff-e2e: audience de produto ausente')
if int(claims.get('exp', 0)) <= int(time.time()):
    raise SystemExit('reader-bff-e2e: token expirado')
if 'bookrush.catalog.read' not in claims.get('scope', '').split():
    raise SystemExit('reader-bff-e2e: scope bookrush.catalog.read ausente')
print('oidc_claims=valid')
PY

echo '[3/5] composição autenticada do reader BFF'
auth_header="Authorization: Bearer $access_token"
for endpoint in feed library recent streak; do
  path="/api/v1/reader/$endpoint"
  [[ "$endpoint" == feed ]] && path="$path?size=1"
  status=$(status_without_body "$tmp_dir/$endpoint" "$base_url$path" -H "$auth_header")
  assert_status "authenticated_$endpoint" 200 "$status"
done
for endpoint in progress bookmarks; do
  status=$(status_without_body "$tmp_dir/$endpoint" "$base_url/api/v1/reader/books/$E2E_BOOK_ID/$endpoint" -H "$auth_header")
  assert_status "authenticated_$endpoint" 200 "$status"
done

echo '[4/6] perfil e preferências de privacidade autenticados'
status=$(status_without_body "$tmp_dir/profile" "$base_url/api/v1/profile" -H "$auth_header")
assert_status authenticated_profile 200 "$status"
status=$(status_without_body "$tmp_dir/privacy" "$base_url/api/v1/profile/privacy" -H "$auth_header")
assert_status authenticated_privacy 200 "$status"

echo '[5/6] publicação EPUB encaminhada ao content-service'
status=$(status_without_body "$tmp_dir/publication" "$base_url/api/v1/reader/books/$E2E_BOOK_ID/publication.json" -H "$auth_header")
if [[ "$status" == 200 ]]; then
  printf 'authenticated_publication=%s\n' "$status"
elif [[ "$status" == 404 ]]; then
  printf 'authenticated_publication=%s (EPUB não publicado para E2E_BOOK_ID)\n' "$status"
else
  echo "reader-bff-e2e: authenticated_publication esperado HTTP 200 ou 404, recebido $status" >&2
  exit 1
fi

echo '[6/6] preflight CORS da origem do cliente'
status=$(curl -fsS -D "$tmp_dir/cors.headers" -o "$tmp_dir/cors.body" -w '%{http_code}' -X OPTIONS "$base_url/api/v1/reader/feed" \
  -H "Origin: $origin" -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: authorization')
if [[ "$status" != 2* ]]; then
  echo "reader-bff-e2e: preflight CORS retornou HTTP $status" >&2
  exit 1
fi
allow_origin=$(awk -F': ' 'tolower($1)=="access-control-allow-origin"{gsub("\r", "", $2); print $2}' "$tmp_dir/cors.headers" | tail -1)
[[ "$allow_origin" == "$origin" ]] || {
  echo 'reader-bff-e2e: Access-Control-Allow-Origin não corresponde à origem testada' >&2
  exit 1
}
printf 'cors_preflight=%s\n' "$status"
echo 'Reader BFF public integration: OK'
