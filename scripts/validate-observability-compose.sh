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

app_compose="$repo_root/infrastructure/compose.yaml"
prometheus_config="$repo_root/infrastructure/observability/prometheus/prometheus.yml"
grep -Fq -- '--entrypoints.metrics.address=:8082' "$app_compose" \
  && grep -Fq -- '--metrics.prometheus.entrypoint=metrics' "$app_compose" \
  && grep -Fq -- '--metrics.prometheus.addrouterslabels=false' "$app_compose" \
  && grep -Fq -- '--metrics.prometheus.addserviceslabels=false' "$app_compose" || {
  echo "Traefik must expose low-cardinality Prometheus metrics on its private metrics entrypoint" >&2
  exit 1
}
grep -Fq -- '--metrics-enabled=true' "$app_compose" \
  && grep -Fq -- '--http-metrics-histograms-enabled=true' "$app_compose" \
  && grep -Fq -- '--http-management-port=9000' "$app_compose" || {
  echo "Keycloak HTTP metrics and histograms must be enabled on the private management port" >&2
  exit 1
}
grep -Fq 'targets: ["reverse-proxy:8082"]' "$prometheus_config" \
  && grep -Fq 'targets: ["keycloak:9000"]' "$prometheus_config" || {
  echo "Prometheus must scrape Traefik and Keycloak on their private endpoints" >&2
  exit 1
}
ops_dashboard="$repo_root/infrastructure/observability/grafana/dashboards/operations.json"
grep -Fq 'OP-01 Availability by scrape target' "$ops_dashboard" \
  && grep -Fq 'job=~\"bookrush-java|postgres-exporter|redis-exporter|cadvisor|node-exporter|traefik|keycloak\"' "$ops_dashboard" \
  && grep -Fq 'node-exporter|traefik"} == 0' "$repo_root/infrastructure/observability/prometheus/alerts.yml" || {
  echo "OP-01 and target-down alert must cover the configured scrape jobs" >&2
  exit 1
}
grep -Fq 'sum by (service, uri) (rate(http_server_requests_seconds_count{job=\"bookrush-java\"' "$ops_dashboard" \
  && grep -Fq '{{service}} {{uri}} req/s' "$ops_dashboard" || {
  echo "OP-02 dashboard must break Java request throughput down by service and route template" >&2
  exit 1
}
grep -Fq 'sum by (service, uri, status) (rate(http_server_requests_seconds_count{job=\"bookrush-java\"' "$ops_dashboard" \
  && grep -Fq '{{service}} {{uri}} {{status}}' "$ops_dashboard" || {
  echo "OP-03 dashboard must break Java HTTP errors down by service, route template and status" >&2
  exit 1
}
grep -Fq 'sum by (service, uri, le) (rate(http_server_requests_seconds_bucket{job=\"bookrush-java\"' "$ops_dashboard" \
  && grep -Fq 'p95 {{service}} {{uri}}' "$ops_dashboard" || {
  echo "OP-04 dashboard must calculate HTTP latency percentiles by service and route template" >&2
  exit 1
}
grep -Fq 'traefik_entrypoint_requests_total' "$ops_dashboard" \
  && grep -Fq 'traefik_entrypoint_request_duration_seconds_bucket' "$ops_dashboard" \
  && grep -Fq 'http_server_requests_seconds_count{job=\"keycloak\"' "$ops_dashboard" \
  && grep -Fq 'http_server_requests_seconds_bucket{job=\"keycloak\"' "$ops_dashboard" || {
  echo "OP-09 dashboard must use the configured Traefik and Keycloak HTTP metric families" >&2
  exit 1
}
grep -Fq 'pg_stat_database_blks_hit' "$ops_dashboard" \
  && grep -Fq 'pg_stat_database_blks_read' "$ops_dashboard" \
  && grep -Fq 'pg_stat_database_xact_commit' "$ops_dashboard" \
  && grep -Fq 'pg_stat_database_xact_rollback' "$ops_dashboard" || {
  echo "OP-06 dashboard must use PostgreSQL database hit/read/transaction counters" >&2
  exit 1
}
grep -Fq 'OP-03 HTTP 5xx ratio by Java service' "$ops_dashboard" \
  && grep -Fq ' > 0)' "$ops_dashboard" \
  && grep -Fq 'job=\"bookrush-java\"' "$ops_dashboard" \
  && grep -Fq '])) > 0.1)' "$repo_root/infrastructure/observability/prometheus/alerts.yml" \
  && grep -Fq 'job="bookrush-java"' "$repo_root/infrastructure/observability/prometheus/alerts.yml" || {
  echo "Java HTTP panels/alerts must have a traffic guard and stay isolated from Keycloak" >&2
  exit 1
}
if grep -Eq '127\.0\.0\.1:[^"[:space:]]*:(8082|9000)("|$)' "$app_compose"; then
  echo "Traefik/Keycloak metrics ports must not be published on the host" >&2
  exit 1
fi

grep -Fq 'observability_config:/etc/bookrush-observability:ro' "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "observability configuration must be supplied via Docker named volume" >&2
  exit 1
}
grep -Fq 'docker run --rm -i --network none -v "$config_volume:/config"' "$repo_root/infrastructure/jenkins/deploy-observability.sh" || {
  echo "Jenkins deployment must stream config into the Docker named volume" >&2
  exit 1
}
grep -Fq "scmFor('infrastructure/jenkins/observability.Jenkinsfile')" "$repo_root/infrastructure/jenkins/init.groovy.d/bookrush.groovy" || {
  echo "Jenkins observability job must load its Jenkinsfile from the repository path" >&2
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
grep -Fq 'GF_AUTH_GENERIC_OAUTH_SCOPES: openid profile email' "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "Grafana OAuth must request only Keycloak scopes configured for its client" >&2
  exit 1
}
grep -Fq 'GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH: ${OBS_GRAFANA_OIDC_EMAIL_ATTRIBUTE_PATH:-email || preferred_username}' "$repo_root/infrastructure/observability/compose.yaml" || {
  echo "Grafana OAuth must use the username claim when Keycloak has no email" >&2
  exit 1
}
test -f "$repo_root/infrastructure/nginx/conf.d/bookrush-grafana.conf" || {
  echo "Nginx bootstrap vhost for public Grafana is missing" >&2
  exit 1
}
grafana_vhost_installer="$repo_root/infrastructure/nginx/install-bookrush-grafana-vhost.sh"
test -f "$grafana_vhost_installer" || {
  echo "targeted Grafana vhost installer is missing" >&2
  exit 1
}
grep -Fq 'install -m 0644 "$source_file" "$target_file"' "$grafana_vhost_installer" || {
  echo "Grafana vhost installer must only copy the bootstrap file" >&2
  exit 1
}
if grep -Eq '(^|[[:space:]])(nginx|certbot)([[:space:]]|$)' "$grafana_vhost_installer"; then
  echo "Grafana vhost installer must leave validation/reload/certbot to the operator" >&2
  exit 1
fi

echo "Observability Compose: OK"
