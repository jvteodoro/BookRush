#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-compose-test.XXXXXXXX")"
project="$(basename "$run_dir" | tr '[:upper:].' '[:lower:]-')"
export BOOKRUSH_PROXY_PORT=0 BOOKRUSH_S3_PORT=0 BOOKRUSH_FRONTEND_PORT=0 BOOKRUSH_PGADMIN_PORT=0
export BOOKRUSH_INGESTION_PORT=0 BOOKRUSH_ANALYTICS_PORT=0
# Compose gives shell variables precedence over --env-file. Pin disposable
# database credentials so a developer's exported production .env cannot leak
# into this isolated project or make the application/container disagree.
export POSTGRES_DB=bookrush POSTGRES_USER=bookrush POSTGRES_PASSWORD=bookrush
# This smoke intentionally exercises the application stack without the optional
# Keycloak profile. OIDC is covered by the dedicated Keycloak test profile.
export CATALOG_OIDC_ENABLED=false INGESTION_SECURITY_ENABLED=false
export BACKEND_IMAGE="$project/api" FRONTEND_IMAGE="$project/frontend"
compose=(docker compose --project-name "$project" -f "$repo_dir/infrastructure/compose.yaml" --env-file "$repo_dir/.env.example")
cleanup() {
  local status=$?
  trap - EXIT
  if ((status != 0)); then
    echo '--- compose test diagnostics ---' >&2
    "${compose[@]}" ps >&2 || true
    "${compose[@]}" logs --no-color --tail=120 catalog-service book-ingestion-service book-analytics-service >&2 || true
  fi
  "${compose[@]}" down --volumes --remove-orphans || true
  rmdir "$run_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${compose[@]}" up -d --build --wait --wait-timeout 180 seaweedfs
BOOKRUSH_S3_PORT="$("${compose[@]}" port seaweedfs 8333 | awk -F: '{print $NF}')"
export BOOKRUSH_S3_PORT
export STORAGE_PUBLIC_ENDPOINT="http://localhost:$BOOKRUSH_S3_PORT"
"${compose[@]}" up -d --build --wait --wait-timeout 240
# Test inside frontend, avoiding host port assumptions / Jenkins loopback differences.
"${compose[@]}" exec -T frontend wget -q -O - http://127.0.0.1:8080/api/status
echo
echo 'Application Compose (PostgreSQL, Redis, SeaweedFS, API, frontend, pgAdmin): OK'
