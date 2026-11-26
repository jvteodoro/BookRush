#!/usr/bin/env bash
# Validate the real Compose merge without starting services or reading secrets.
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT
for variable in BACKEND_IMAGE FRONTEND_IMAGE INGESTION_IMAGE ANALYTICS_IMAGE ANALYTICS_RUNTIME_IMAGE BEHAVIOR_IMAGE RECOMMENDATION_IMAGE READER_BFF_IMAGE READER_STATE_IMAGE READER_PROFILE_IMAGE SOCIAL_IMAGE ADMIN_SERVICE_IMAGE ADMIN_WEB_IMAGE PUBLISHER_IMAGE BOOK_CONTENT_IMAGE FAMILY_IMAGE SUBSCRIPTION_IMAGE SEAWEEDFS_IMAGE KEYCLOAK_IMAGE; do
  export "$variable=contract/bookrush/${variable,,}:revision"
done
export KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token
export KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush
export KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service
# Fixture only. Never use the local/protected env file in this test.
export KEYCLOAK_ADMIN_CLIENT_SECRET=compose-contract-fixture
export BOOKRUSH_PROTECTED_DISTRIBUTION=true BOOKRUSH_STORAGE_EDGE_CLOSED=true
export BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG="$(python3 scripts/render-traefik-config.py infrastructure/traefik/dynamic.yaml)"
cat > "$workspace/old.compose.yaml" <<'YAML'
services:
  reverse-proxy:
    image: traefik:v3.5.3
    command: [--providers.file.filename=/old-checkout/dynamic.yaml, --ping=true]
  admin-service:
    image: old/admin:stale
    environment:
      KEYCLOAK_ADMIN_BASE: http://keycloak:8080/admin/realms/bookrush-platform
      UNRELATED_HOST_SETTING: preserve
YAML
for base in infrastructure/compose.yaml "$workspace/old.compose.yaml"; do
  compose_files=(-f "$base")
  profiles=(--profile auth)
  if [[ "$base" == infrastructure/compose.yaml ]]; then
    compose_files+=(-f infrastructure/compose.subscription.yaml -f infrastructure/compose.family.yaml)
    profiles+=(--profile subscription --profile family)
  fi
  docker compose --project-name bookrush-contracts --env-file .env.example "${profiles[@]}" \
    "${compose_files[@]}" -f infrastructure/jenkins/deploy-images.compose.yaml config --format json |
    docker run --rm -i --network none python:3.12-slim python3 -c '
import json, sys
services = json.load(sys.stdin)["services"]
gateway = services["reverse-proxy"]
routes = gateway["environment"]["BOOKRUSH_TRAEFIK_DYNAMIC_CONFIG"]
assert "PathPrefix(`/api/v1/reader/library/`)" in routes
assert "/api/v1/family" in routes and "/api/v1/subscription" in routes
assert "{{" not in routes and "}}" not in routes
assert gateway["entrypoint"][:2] == ["/bin/sh", "-ec"]
assert "exec /entrypoint.sh" in gateway["entrypoint"][2]
assert "--providers.file.filename=/tmp/bookrush-traefik-dynamic.yaml" in gateway["entrypoint"][2]
assert "--ping=true" in gateway["command"]
env = services["admin-service"]["environment"]
expected = {
    "KEYCLOAK_ADMIN_TOKEN_URI": "http://keycloak:8080/realms/bookrush/protocol/openid-connect/token",
    "KEYCLOAK_ADMIN_BASE": "http://keycloak:8080/admin/realms/bookrush",
    "KEYCLOAK_ADMIN_CLIENT_ID": "bookrush-admin-service",
    "KEYCLOAK_ADMIN_CLIENT_SECRET": "compose-contract-fixture",
}
for key, value in expected.items():
    assert env.get(key) == value, "Configuração administrativa incorreta: " + key
if "UNRELATED_HOST_SETTING" in env:
    assert env["UNRELATED_HOST_SETTING"] == "preserve"
for variable, service in {
    "BACKEND_IMAGE": "catalog-service", "FRONTEND_IMAGE": "frontend",
    "INGESTION_IMAGE": "book-ingestion-service", "ANALYTICS_IMAGE": "book-analytics-service",
    "ANALYTICS_RUNTIME_IMAGE": "book-analytics-runtime", "RECOMMENDATION_IMAGE": "recommendation-service",
    "READER_BFF_IMAGE": "reader-bff-service", "READER_STATE_IMAGE": "reader-state-service",
    "READER_PROFILE_IMAGE": "reader-profile-service",
    "SOCIAL_IMAGE": "social-service", "ADMIN_SERVICE_IMAGE": "admin-service", "ADMIN_WEB_IMAGE": "admin-web",
    "PUBLISHER_IMAGE": "publisher-service", "BOOK_CONTENT_IMAGE": "book-content-service",
    "FAMILY_IMAGE": "family-service", "SUBSCRIPTION_IMAGE": "subscription-service",
    "SEAWEEDFS_IMAGE": "seaweedfs", "KEYCLOAK_IMAGE": "keycloak",
}.items():
    if service in services:
        assert services[service]["image"] == "contract/bookrush/" + variable.lower() + ":revision", service
print("Compose merge: imagens, configuração administrativa e rotas da revisão OK")
'
done
