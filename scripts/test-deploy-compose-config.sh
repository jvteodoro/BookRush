#!/usr/bin/env bash
# Validate the real Compose merge without starting services or reading secrets.
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT
for variable in BACKEND_IMAGE FRONTEND_IMAGE INGESTION_IMAGE ANALYTICS_IMAGE ANALYTICS_RUNTIME_IMAGE RECOMMENDATION_IMAGE READER_BFF_IMAGE READER_STATE_IMAGE READER_PROFILE_IMAGE SOCIAL_IMAGE ADMIN_SERVICE_IMAGE ADMIN_WEB_IMAGE; do
  export "$variable=contract/bookrush/${variable,,}:revision"
done
export KEYCLOAK_ADMIN_TOKEN_URI=http://keycloak:8080/realms/bookrush/protocol/openid-connect/token
export KEYCLOAK_ADMIN_BASE=http://keycloak:8080/admin/realms/bookrush
export KEYCLOAK_ADMIN_CLIENT_ID=bookrush-admin-service
# Fixture only. Never use the local/protected env file in this test.
export KEYCLOAK_ADMIN_CLIENT_SECRET=compose-contract-fixture
cat > "$workspace/old.compose.yaml" <<'YAML'
services:
  admin-service:
    image: old/admin:stale
    environment:
      KEYCLOAK_ADMIN_BASE: http://keycloak:8080/admin/realms/bookrush-platform
      UNRELATED_HOST_SETTING: preserve
YAML
for base in infrastructure/compose.yaml "$workspace/old.compose.yaml"; do
  docker compose --project-name bookrush-contracts --env-file .env.example \
    -f "$base" -f infrastructure/jenkins/deploy-images.compose.yaml config --format json |
    docker run --rm -i --network none python:3.12-slim python3 -c '
import json, sys
services = json.load(sys.stdin)["services"]
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
}.items():
    assert services[service]["image"] == "contract/bookrush/" + variable.lower() + ":revision", service
print("Compose merge: imagens e configuração administrativa OK")
'
done
