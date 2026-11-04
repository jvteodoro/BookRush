#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose_file="$repo_root/infrastructure/observability/compose.yaml"

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }

# These values exist only for Compose interpolation validation. Production
# values must come from the protected environment used by the operator.
export OBS_GRAFANA_ADMIN_PASSWORD="fixture-observability-password"
export OBS_POSTGRES_EXPORTER_DATA_SOURCE_NAME="postgresql://bookrush:bookrush@postgres:5432/bookrush?sslmode=disable"

docker compose -f "$compose_file" config --quiet
services=$(docker compose -f "$compose_file" config --services)
for service in prometheus grafana loki tempo alloy node-exporter cadvisor postgres-exporter redis-exporter; do
  grep -Fxq "$service" <<<"$services" || {
    echo "missing observability service: $service" >&2
    exit 1
  }
done

grep -Fq 'target_label: service' "$repo_root/infrastructure/observability/prometheus/prometheus.yml" || {
  echo "Prometheus Java targets must expose the dashboard service label" >&2
  exit 1
}

echo "Observability Compose: OK"
