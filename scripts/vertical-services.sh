#!/usr/bin/env bash
set -Eeuo pipefail

vertical="${1:-}"
case "$vertical" in
  catalog) printf '%s\n' catalog-service book-content-service ;;
  ingestion) printf '%s\n' book-ingestion-service catalog-service ;;
  reader) printf '%s\n' reader-bff-service reader-state-service reader-profile-service recommendation-service book-content-service ;;
  social) printf '%s\n' social-service behavior-service reader-profile-service ;;
  analytics) printf '%s\n' book-analytics-service recommendation-service ;;
  publishing) printf '%s\n' publisher-service admin-service ;;
  platform) printf '%s\n' catalog-service book-ingestion-service book-analytics-service reader-bff-service reader-state-service reader-profile-service social-service behavior-service recommendation-service publisher-service admin-service book-content-service ;;
  *)
    echo "vertical inválido: ${vertical:-<vazio>}" >&2
    echo 'válidos: catalog ingestion reader social analytics publishing platform' >&2
    exit 2
    ;;
esac
