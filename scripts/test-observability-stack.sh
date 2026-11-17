#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infrastructure/observability/compose.yaml"
project="bookrush-observability-smoke-$$"
network="${project}-network"
grafana_port=$((13000 + ($$ % 500)))
env_file="$(mktemp /tmp/bookrush-observability-smoke.XXXXXX)"

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

export OBS_GRAFANA_ADMIN_PASSWORD="fixture-observability-password"
export OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME="postgresql://bookrush:bookrush@postgres:5432/bookrush?sslmode=disable"
export BOOKRUSH_NETWORK_NAME="$network"
export OBSERVABILITY_NETWORK_NAME="${project}-observability"
export OBS_CONFIG_VOLUME="${project}-config"
export OBS_GRAFANA_PORT="$grafana_port"
export OBS_GRAFANA_ROOT_URL="http://127.0.0.1:${grafana_port}"
export OBSERVABILITY_COMPOSE_NAME="$project"
export OBSERVABILITY_ENV_FILE="$env_file"
umask 077
printf '%s\n' \
  "OBS_GRAFANA_ADMIN_PASSWORD=$OBS_GRAFANA_ADMIN_PASSWORD" \
  "OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME=$OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME" \
  "OBS_CONFIG_VOLUME=$OBS_CONFIG_VOLUME" \
  "OBSERVABILITY_COMPOSE_NAME=$OBSERVABILITY_COMPOSE_NAME" \
  "OBSERVABILITY_NETWORK_NAME=$OBSERVABILITY_NETWORK_NAME" \
  "OBS_GRAFANA_PORT=$OBS_GRAFANA_PORT" \
  "OBS_GRAFANA_ROOT_URL=$OBS_GRAFANA_ROOT_URL" \
  "BOOKRUSH_NETWORK_NAME=$BOOKRUSH_NETWORK_NAME" > "$env_file"

cleanup() {
  docker compose -p "$project" -f "$compose_file" down -v --remove-orphans >/dev/null 2>&1 || true
  docker volume rm "$OBS_CONFIG_VOLUME" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f "$env_file"
}
trap cleanup EXIT

docker network create "$network" >/dev/null
bash "$repo_root/infrastructure/jenkins/deploy-observability.sh" >/dev/null

for service in prometheus grafana loki tempo alloy node-exporter cadvisor postgres-exporter redis-exporter; do
  container=$(docker compose -p "$project" -f "$compose_file" ps -q "$service")
  health=$(docker inspect -f '{{.State.Health.Status}}' "$container")
  [[ "$health" == healthy ]] || {
    echo "$service is not healthy: $health" >&2
    exit 1
  }
done

curl -fsS "http://127.0.0.1:${grafana_port}/api/health" >/dev/null
dashboards=$(curl -fsS -u "admin:${OBS_GRAFANA_ADMIN_PASSWORD}" \
  "http://127.0.0.1:${grafana_port}/api/search?type=dash-db")
datasources=$(curl -fsS -u "admin:${OBS_GRAFANA_ADMIN_PASSWORD}" \
  "http://127.0.0.1:${grafana_port}/api/datasources")
dashboard_count=$(grep -o '"uid":"bookrush-[^"]*"' <<<"$dashboards" | wc -l)
datasource_count=$(grep -o '"type":"\(prometheus\|loki\|tempo\)"' <<<"$datasources" | wc -l)
(( dashboard_count >= 4 )) || { echo "expected four Grafana dashboards" >&2; exit 1; }
(( datasource_count >= 3 )) || { echo "expected Prometheus/Loki/Tempo datasources" >&2; exit 1; }

prometheus=$(docker compose -p "$project" -f "$compose_file" ps -q prometheus)
docker exec "$prometheus" wget -qO- http://127.0.0.1:9090/-/ready >/dev/null
rules=$(docker exec "$prometheus" wget -qO- http://127.0.0.1:9090/api/v1/rules)
grep -q 'BookRush' <<<"$rules" || { echo "BookRush rules were not loaded" >&2; exit 1; }

trace_id="$(printf '%032x' "$$")"
span_id="$(printf '%016x' "$$")"
trace_id_b64="$(python3 -c 'import base64,sys; print(base64.b64encode(bytes.fromhex(sys.argv[1])).decode())' "$trace_id")"
span_id_b64="$(python3 -c 'import base64,sys; print(base64.b64encode(bytes.fromhex(sys.argv[1])).decode())' "$span_id")"
now_ns="$(date +%s%N)"
log_marker="bookrush-observability-smoke-$$"
trace_payload="{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"bookrush-observability-smoke\"}}]},\"scopeSpans\":[{\"spans\":[{\"traceId\":\"$trace_id\",\"spanId\":\"$span_id\",\"name\":\"smoke.request\",\"kind\":1,\"startTimeUnixNano\":\"$now_ns\",\"endTimeUnixNano\":\"$now_ns\"}]}]}]}"
log_payload="{\"resourceLogs\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"bookrush-observability-smoke\"}}]},\"scopeLogs\":[{\"logRecords\":[{\"timeUnixNano\":\"$now_ns\",\"severityNumber\":9,\"severityText\":\"INFO\",\"body\":{\"stringValue\":\"$log_marker\"}}]}]}]}"
trace_ingest_response=$(docker run --rm --network "$OBSERVABILITY_NETWORK_NAME" curlimages/curl:8.10.1 \
  -sS -w '\nHTTP %{http_code}' -H 'Content-Type: application/json' -d "$trace_payload" http://alloy:4318/v1/traces)
grep -q 'HTTP 200' <<<"$trace_ingest_response" || { echo "Alloy rejected OTLP trace: $trace_ingest_response" >&2; exit 1; }
log_ingest_response=$(docker run --rm --network "$OBSERVABILITY_NETWORK_NAME" curlimages/curl:8.10.1 \
  -sS -w '\nHTTP %{http_code}' -H 'Content-Type: application/json' -d "$log_payload" http://alloy:4318/v1/logs)
grep -q 'HTTP 200' <<<"$log_ingest_response" || { echo "Alloy rejected OTLP log: $log_ingest_response" >&2; exit 1; }

trace_found=false
log_found=false
for attempt in {1..20}; do
  trace_response="$(docker run --rm --network "$OBSERVABILITY_NETWORK_NAME" curlimages/curl:8.10.1 \
    -fsS "http://tempo:3200/api/traces/$trace_id" 2>/dev/null || true)"
  if grep -q "$trace_id_b64" <<<"$trace_response"; then trace_found=true; fi
  log_response="$(docker run --rm --network "$OBSERVABILITY_NETWORK_NAME" curlimages/curl:8.10.1 \
    --get -fsS --data-urlencode "query={service_name=\"bookrush-observability-smoke\"} |= \"$log_marker\"" \
    http://loki:3100/loki/api/v1/query_range 2>/dev/null || true)"
  if grep -q "$log_marker" <<<"$log_response"; then log_found=true; fi
  [[ "$trace_found" == true && "$log_found" == true ]] && break
  sleep 1
done
if [[ "$trace_found" != true ]]; then
  echo 'OTLP trace was not queryable in Tempo; response and collector logs follow' >&2
  printf '%s\n' "$trace_response" >&2
  docker compose -p "$project" -f "$compose_file" logs --no-color --tail=80 alloy tempo >&2 || true
  exit 1
fi
if [[ "$log_found" != true ]]; then
  echo 'OTLP log was not queryable in Loki; response and collector logs follow' >&2
  printf '%s\n' "$log_response" >&2
  docker compose -p "$project" -f "$compose_file" logs --no-color --tail=80 alloy loki >&2 || true
  exit 1
fi

echo "Observability stack smoke: OK (project=$project dashboards=$dashboard_count datasources=$datasource_count trace=Tempo log=Loki)"
