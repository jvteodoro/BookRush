#!/usr/bin/env bash
set -Eeuo pipefail

# Reconcile the persistent platform realm from a Jenkins Docker agent. The
# agent workspace is a named volume, so helper containers share it explicitly
# instead of using a host bind mount that the Docker daemon cannot see.
: "${KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD:?KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD ausente}"
: "${INGESTION_ADMIN_CLIENT_SECRET:?INGESTION_ADMIN_CLIENT_SECRET ausente}"
: "${BOOKRUSH_NETWORK_NAME:=bookrush_bookrush}"
command -v docker >/dev/null || { echo 'Docker ausente.' >&2; exit 2; }
docker info >/dev/null 2>&1 || { echo 'Usuário sem acesso ao Docker.' >&2; exit 2; }

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
config_volume="bookrush-keycloak-config-${BUILD_NUMBER:-manual}-$$"
cleanup() { docker volume rm -f "$config_volume" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker volume create "$config_volume" >/dev/null

# Keep secrets already provisioned in the persistent realm. The agent owns
# the ingestion credential, but must not require or replace the existing
# admin-service secret when it is intentionally unavailable to Jenkins.
# Render into a dedicated Docker volume. The Jenkins workspace may be a bind
# mount that is not inherited by sibling containers through --volumes-from.
docker run --rm --volumes-from "$HOSTNAME" -v "$config_volume:/config" -w "$repo_dir" \
  -e JENKINS_OIDC_CLIENT_SECRET \
  -e BACKSTAGE_OIDC_CLIENT_SECRET \
  -e INGESTION_ADMIN_CLIENT_SECRET \
  -e INGESTION_CANONICAL_CLIENT_SECRET \
  -e INGESTION_ASSET_CLIENT_SECRET \
  -e SEAWEEDFS_OIDC_CLIENT_SECRET \
  -e FEED_EXCERPTS_CLIENT_SECRET \
  -e KEYCLOAK_ADMIN_CLIENT_SECRET \
  -e KEYCLOAK_RECONCILE_REQUIRE_CLIENT_SECRETS=false \
  python:3.12-slim python3 infrastructure/keycloak/render-config.py \
  infrastructure/keycloak /config

# The config-cli container reads only the dedicated ephemeral volume and never
# writes to the persistent Keycloak data volume.
docker run --rm --user 0:0 -v "$config_volume:/config:ro" --network "$BOOKRUSH_NETWORK_NAME" \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}" \
  -e KEYCLOAK_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" \
  -e KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
  -e IMPORT_FILES_LOCATIONS='/config/*.json' \
  -e IMPORT_VARSUBSTITUTION_ENABLED=true \
  adorsys/keycloak-config-cli:6.3.0-18.0.2

# keycloak-config-cli does not reliably update role mappings for an existing
# service-account user. Reapply only the ingestion operator role idempotently;
# no client secret is read or rotated by this step.
docker run --rm -i --network "$BOOKRUSH_NETWORK_NAME" \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}" \
  -e KEYCLOAK_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" \
  python:3.12-slim python3 - < infrastructure/keycloak/grant-ingestion-service-role.py

docker run --rm -i --network "$BOOKRUSH_NETWORK_NAME" \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}" \
  -e KEYCLOAK_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" \
  python:3.12-slim python3 - < infrastructure/keycloak/grant-user-management-roles.py

docker run --rm -i --network "$BOOKRUSH_NETWORK_NAME" \
  -e KEYCLOAK_URL=http://keycloak:8080 \
  -e KEYCLOAK_USER="${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}" \
  -e KEYCLOAK_PASSWORD="$KEYCLOAK_CONFIG_CLI_ADMIN_PASSWORD" \
  -e ADMIN_WEB_ADMIN_USERNAME="${ADMIN_WEB_ADMIN_USERNAME:-${KEYCLOAK_CONFIG_CLI_ADMIN_USER:-admin}}" \
  python:3.12-slim python3 - < infrastructure/keycloak/grant-admin-console-access.py

echo 'Keycloak persistent realm reconciliado pelo volume do agente.'
