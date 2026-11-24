#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
# Build the selected checkout only when Jenkins has not supplied its versioned images.
if [[ -z "${CATALOG_TEST_IMAGE:-}" ]]; then
  export CATALOG_TEST_IMAGE=bookrush/catalog-publishing-test
  docker build -t "$CATALOG_TEST_IMAGE" -f services/catalog-service/Dockerfile .
fi
if [[ -z "${INGESTION_TEST_IMAGE:-}" ]]; then
  export INGESTION_TEST_IMAGE=bookrush/ingestion-publishing-test
  docker build -t "$INGESTION_TEST_IMAGE" -f services/book-ingestion-service/Dockerfile .
fi
if [[ -z "${PUBLISHER_TEST_IMAGE:-}" ]]; then
  export PUBLISHER_TEST_IMAGE=bookrush/publisher-publishing-test
  docker build -t "$PUBLISHER_TEST_IMAGE" -f services/publisher-service/Dockerfile .
fi
if [[ -z "${CATALOG_MIGRATION_TEST_IMAGE:-}" ]]; then
  export CATALOG_MIGRATION_TEST_IMAGE=bookrush/catalog-publishing-migration-test
  docker build --target build -t "$CATALOG_MIGRATION_TEST_IMAGE" -f services/catalog-service/Dockerfile .
fi
project="publishing-test-$(date +%s)-$$"
compose=(docker compose -p "$project" -f infrastructure/compose.publishing-test.yaml)
cleanup() {
  status=$?
  trap - EXIT
  if (( status != 0 )); then "${compose[@]}" logs --tail=100 catalog ingestion publisher >&2 || true; fi
  "${compose[@]}" down --volumes --remove-orphans || true
  exit "$status"
}
trap cleanup EXIT
"${compose[@]}" up -d --wait postgres
"${compose[@]}" run --rm migration-tests
"${compose[@]}" up --build --abort-on-container-exit --exit-code-from tests tests
