#!/usr/bin/env bash
set -Eeuo pipefail

vertical="${1:-}"
mapfile -t services < <(bash scripts/vertical-services.sh "$vertical")

for service in "${services[@]}"; do
  echo "==> $vertical / $service"
  bash scripts/test-microservice.sh "$service"
done

case "$vertical" in
  catalog|ingestion) bash scripts/test-bibliographic-e2e.sh ;;
  analytics) bash scripts/test-analytics-e2e.sh ;;
  platform)
    bash scripts/validate-pipelines.sh
    bash scripts/test-keycloak-realms.sh
    bash scripts/test-keycloak-reconcile.sh
    ;;
esac
echo "Vertical $vertical: validação concluída"
