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

grep -Fq 'observability_config:/etc/bookrush-observability:ro' "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "observability configuration must be supplied via Docker named volume" >&2
  exit 1
}
grep -Fq 'docker run --rm -i --network none -v "$config_volume:/config"' "$repo_root/infrastructure/jenkins/deploy-observability.sh" || {
  echo "Jenkins deployment must stream config into the Docker named volume" >&2
  exit 1
}
grep -Fq 'Host(`grafana-bookrush.jteodoro.tec.br`)' "$repo_root/infrastructure/traefik/dynamic.yaml" || {
  echo "Traefik must route the Grafana public hostname" >&2
  exit 1
}
grep -Fq 'GF_AUTH_GENERIC_OAUTH_ALLOW_ASSIGN_GRAFANA_ADMIN: "true"' "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "Grafana OAuth must explicitly permit the configured platform-admin mapping" >&2
  exit 1
}
grep -Fq "contains(groups[*], 'platform-operators')" "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "Grafana OAuth must map platform-operators to the operator role" >&2
  exit 1
}
test -f "$repo_root/infrastructure/nginx/conf.d/bookrush-grafana.conf" || {
  echo "Nginx bootstrap vhost for public Grafana is missing" >&2
  exit 1
}

echo "Observability Compose: OK"
