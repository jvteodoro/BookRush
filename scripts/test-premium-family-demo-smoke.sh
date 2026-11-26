#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${BOOKRUSH_DEMO_ENV_FILE:-}"
[[ -n "$env_file" && -r "$env_file" ]] || { echo 'Credential demo ausente no smoke.' >&2; exit 2; }
xtrace=false; [[ $- == *x* ]] && xtrace=true; set +x
set -a; . "$env_file"; set +a
[[ "$xtrace" == true ]] && set -x
export BOOKRUSH_DEMO_BASE_URL=http://localhost:19080
mkdir -p "$repo_dir/artifacts"

status() {
  docker run --rm --network host curlimages/curl:8.10.1 -sS --max-time 10 \
    -o /dev/null -w '%{http_code}' "$1" 2>/dev/null || true
}
for path in /api/v1/session /api/v1/family/me /api/v1/subscription/me \
  /api/internal/v1/product-access/decisions; do
  code="$(status "http://localhost:19081$path")"
  case "$code" in
    401|403|404) ;;
    *) echo "Gateway demo falhou para rota sem token: HTTP ${code:-000}." >&2; exit 1;;
  esac
done
docker run --rm --network host curlimages/curl:8.10.1 -fsS --max-time 10 \
  http://localhost:19081/ >/dev/null

for service in postgres keycloak seaweedfs catalog-service book-content-service \
  reader-bff-service reader-state-service social-service reader-profile-service \
  behavior-service publisher-service subscription-service family-service frontend reverse-proxy; do
  cid="$(docker ps -q --filter "label=com.docker.compose.project=bookrush-demo" \
    --filter "label=com.docker.compose.service=$service")"
  [[ -n "$cid" ]] || { echo "Healthcheck demo ausente: $service" >&2; exit 1; }
  state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")"
  [[ "$state" == healthy ]] || { echo "Healthcheck demo falhou: $service=$state" >&2; exit 1; }
done

docker run --rm --network host --volumes-from "${HOSTNAME:?Jenkins container id ausente}" \
  -w "$repo_dir/admin-web" -e DEMO_ADULT_USERNAME -e DEMO_ADULT_PASSWORD \
  -e DEMO_MINOR_USERNAME -e DEMO_MINOR_PASSWORD -e BOOKRUSH_DEMO_BASE_URL \
  mcr.microsoft.com/playwright:v1.65.0-noble bash -lc '
    set -euo pipefail
    npm ci --no-audit --no-fund >/dev/null
    mkdir -p ../artifacts
    node ../scripts/test-premium-family-demo-browser.mjs
  '

echo 'Smoke demo pelo gateway, endpoints sem sessão, Keycloak real e Chromium OK.'
