#!/usr/bin/env bash
# No credentials or mutations: verify the browser's library preflight path.
set -Eeuo pipefail
network="${1:-bookrush_bookrush}"
origin="${2:-https://bookrush.jteodoro.tec.br}"
for method in GET PUT DELETE; do
  path=/api/v1/reader/library
  if [[ "$method" != GET ]]; then
    path+=/11111111-1111-4111-8111-111111111111
  fi
  status=000
  for attempt in {1..12}; do
    status="$(docker run --rm --network "$network" curlimages/curl:8.10.1 \
      -sS --connect-timeout 3 --max-time 10 -o /dev/null -w '%{http_code}' \
      -X OPTIONS "http://reverse-proxy$path" \
      -H "Origin: $origin" \
      -H "Access-Control-Request-Method: $method" \
      -H 'Access-Control-Request-Headers: authorization,content-type' 2>/dev/null || true)"
    [[ "$status" == 200 ]] && break
    echo "Smoke da biblioteca: preflight $method retornou HTTP ${status:-000} (tentativa $attempt/12)." >&2
    [[ "$status" == 401 || "$status" == 403 ]] && break
    sleep 5
  done
  if [[ "$status" != 200 ]]; then
    echo "Smoke da biblioteca falhou: $method $path." >&2
    exit 1
  fi
done
echo 'Smoke das rotas de biblioteca pelo gateway: GET/PUT/DELETE OK' >&2
