#!/usr/bin/env bash
set -Eeuo pipefail

vertical="${1:-}"
case "$vertical" in
  catalog) services=(catalog-service book-content-service) ;;
  ingestion) services=(book-ingestion-service catalog-service) ;;
  reader) services=(reader-bff-service reader-state-service reader-profile-service recommendation-service book-content-service) ;;
  social) services=(social-service behavior-service reader-profile-service) ;;
  analytics) services=(book-analytics-service recommendation-service) ;;
  publishing) services=(publisher-service admin-service) ;;
  platform) services=(catalog-service book-ingestion-service book-analytics-service reader-bff-service reader-state-service reader-profile-service social-service behavior-service recommendation-service publisher-service admin-service book-content-service) ;;
  *)
    echo "vertical inválido: ${vertical:-<vazio>}" >&2
    echo 'válidos: catalog ingestion reader social analytics publishing platform' >&2
    exit 2
    ;;
esac

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
