#!/usr/bin/env bash
set -Eeuo pipefail

set -a
source "${E2E_ENV_FILE:-/home/enoch/.config/bookrush/codex-product.env}"
set +a

base="$E2E_BASE_URL"
base="${base%/}"
authority="$E2E_OIDC_AUTHORITY"
client="$E2E_OIDC_CLIENT_ID"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="${BENCHMARK_OUTPUT_DIR:-docs/operations/incidents/evidence/raw/feed-benchmark-$stamp}"
mkdir -p "$out"
requests="$out/requests.tsv"
stats="$out/container-stats.tsv"
printf 'timestamp\tscenario\tpath\tsize\tworker\titeration\tstatus\telapsed_ms\titems\tmodel\tstrategy\twith_excerpt\n' >"$requests"
tmp="$(mktemp -d)"
worker="${ANALYTICS_WORKER_CONTAINER:-bookrush-book-analytics-runtime-1}"
initial_paused="$(docker inspect -f '{{.State.Paused}}' "$worker" 2>/dev/null || echo false)"
paused_by_script=false
cleanup() {
  if [[ "$paused_by_script" == true ]]; then docker unpause "$worker" >/dev/null 2>&1 || true; fi
  rm -rf "$tmp"
}
trap cleanup EXIT

token_login() {
  local verifier challenge state nonce url action callback code
  verifier="$(openssl rand -base64 64 | tr '+/' '-_' | tr -d '=\n' | cut -c1-64)"
  challenge="$(VERIFIER="$verifier" python3 -c 'import base64,hashlib,os; print(base64.urlsafe_b64encode(hashlib.sha256(os.environ["VERIFIER"].encode()).digest()).decode().rstrip("="))')"
  state="$(openssl rand -hex 24)"
  nonce="$(openssl rand -hex 24)"
  url="$authority/protocol/openid-connect/auth?client_id=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1],safe=""))' "$client")&redirect_uri=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1],safe=""))' "$base/auth/callback")&response_type=code&scope=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("openid profile email bookrush.catalog.read",safe=""))')&state=$state&nonce=$nonce&code_challenge=$challenge&code_challenge_method=S256&response_mode=query"
  curl -fsS -L -c "$tmp/cookie" -b "$tmp/cookie" "$url" -o "$tmp/login"
  action="$(python3 - "$tmp/login" <<'PY'
import html,re,sys
m=re.search(r'<form[^>]+action="([^"]+)"',open(sys.argv[1],encoding='utf-8').read())
if not m: raise SystemExit('OIDC login form not found')
print(html.unescape(m.group(1)))
PY
)"
  curl -fsS -D "$tmp/login.headers" -o /dev/null -c "$tmp/cookie" -b "$tmp/cookie" "$action" \
    --data-urlencode "username=$E2E_OIDC_USERNAME" --data-urlencode "password=$E2E_OIDC_PASSWORD" \
    --data-urlencode 'credentialId=' --data-urlencode 'login=Entrar'
  callback="$(awk 'tolower($0) ~ /^location:/{sub(/\r$/,"" ); sub(/^[^:]*:[[:space:]]*/,"" ); print; exit}' "$tmp/login.headers")"
  code="$(python3 - "$callback" <<'PY'
import sys
from urllib.parse import parse_qs,urlsplit
print(parse_qs(urlsplit(sys.argv[1]).query).get('code',[''])[0])
PY
)"
  curl -fsS -X POST "$authority/protocol/openid-connect/token" \
    --data-urlencode grant_type=authorization_code --data-urlencode "client_id=$client" \
    --data-urlencode "redirect_uri=$base/auth/callback" --data-urlencode "code=$code" \
    --data-urlencode "code_verifier=$verifier" | jq -er .access_token
}

access="$(token_login)"
auth_header="Authorization: Bearer $access"

record_body() {
  local body="$1" status="$2" elapsed="$3" scenario="$4" path="$5" size="$6" worker_state="$7" iteration="$8"
  python3 - "$body" "$status" "$elapsed" "$scenario" "$path" "$size" "$worker_state" "$iteration" "$requests" <<'PY'
import json,sys,datetime
body,status,elapsed,scenario,path,size,worker,iteration,out=sys.argv[1:]
try:
    data=json.load(open(body,encoding='utf-8'))
    items=data.get('items',[]) if isinstance(data,dict) else []
    model=data.get('modelVersion','') if isinstance(data,dict) else ''
    strategy=data.get('strategy','') if isinstance(data,dict) else ''
    with_excerpt=sum(1 for x in items if isinstance(x,dict) and x.get('excerpt'))
except Exception:
    items=[]; model=''; strategy=''; with_excerpt=0
ts=datetime.datetime.now(datetime.timezone.utc).isoformat()
with open(out,'a',encoding='utf-8') as f:
    f.write('\t'.join([ts,scenario,path,size,worker,iteration,status,elapsed,str(len(items)),model,strategy,str(with_excerpt)])+'\n')
PY
}

record_request() {
  local scenario="$1" size="$2" worker_state="$3" iteration="$4"
  local body="$tmp/body-${scenario}-${size}-${iteration}" start end status
  start="$(date +%s%3N)"
  status="$(curl -ksS --max-time 20 -o "$body" -w '%{http_code}' "$base/api/v1/reader/feed?size=$size" -H "$auth_header" || true)"
  end="$(date +%s%3N)"
  record_body "$body" "$status" "$((end-start))" "$scenario" public "$size" "$worker_state" "$iteration"
}

snapshot_stats() {
  local label="$1"
  docker stats --no-stream --format "$label\t{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" \
    | rg 'bookrush-(recommendation|reader-bff|book-analytics-runtime|book-analytics-service|postgres)-1' >>"$stats" || true
}

snapshot_stats before
for size in 10 25 50; do
  for i in $(seq 1 "${SEQUENTIAL_REPS:-15}"); do record_request sequential "$size" active "$i"; done
done

parallel_dir="$tmp/parallel"
mkdir -p "$parallel_dir"
for worker_id in $(seq 1 "${CONCURRENCY:-4}"); do
  (
    for i in $(seq 1 "${PARALLEL_REPS:-5}"); do
      body="$parallel_dir/body-${worker_id}-${i}"
      start="$(date +%s%3N)"
      status="$(curl -ksS --max-time 20 -o "$body" -w '%{http_code}' "$base/api/v1/reader/feed?size=50" -H "$auth_header" || true)"
      end="$(date +%s%3N)"
      record_body "$body" "$status" "$((end-start))" parallel public 50 active "${worker_id}.${i}"
    done
  ) &
done
wait

if [[ "$initial_paused" == false ]]; then
  docker pause "$worker" >/dev/null
  paused_by_script=true
  snapshot_stats paused
  for i in $(seq 1 "${PAUSED_REPS:-15}"); do record_request paused 50 paused "$i"; done
  docker unpause "$worker" >/dev/null
  paused_by_script=false
  snapshot_stats resumed
fi

printf 'output=%s\nrequests=%s\nworker_status=%s\n' "$out" "$requests" "$(docker inspect -f '{{.State.Status}} paused={{.State.Paused}}' "$worker")"

