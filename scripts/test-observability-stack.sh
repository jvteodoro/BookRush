#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infrastructure/observability/compose.yaml"
project="bookrush-observability-smoke-$$"
network="${project}-network"
grafana_port=$((13000 + ($$ % 500)))

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

export OBS_GRAFANA_ADMIN_PASSWORD="fixture-observability-password"
export OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME="postgresql://bookrush:bookrush@postgres:5432/bookrush?sslmode=disable"
export BOOKRUSH_NETWORK_NAME="$network"
export OBSERVABILITY_NETWORK_NAME="${project}-observability"
export OBS_GRAFANA_PORT="$grafana_port"
export OBS_GRAFANA_ROOT_URL="http://127.0.0.1:${grafana_port}"

cleanup() {
  docker compose -p "$project" -f "$compose_file" down -v --remove-orphans >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$network" >/dev/null
docker compose -p "$project" -f "$compose_file" config --quiet
docker compose -p "$project" -f "$compose_file" up -d --wait >/dev/null

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

echo "Observability stack smoke: OK (project=$project dashboards=$dashboard_count datasources=$datasource_count)"
