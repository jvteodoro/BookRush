#!/usr/bin/env bash
set -euo pipefail

# Offline analytics acceptance: the deterministic fixtures exercise the same
# stages used by the worker. No model or source download is allowed here.
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
log=$(mktemp)
trap 'rm -f "$log"' EXIT

docker build --target build -t bookrush/analytics-e2e services/book-analytics-service >/dev/null
docker run --rm --entrypoint mvn bookrush/analytics-e2e test -DskipTests=false | tee "$log"
grep -q 'BUILD SUCCESS' "$log"

echo "Analytics E2E offline passed: deterministic excerpt/features/ranking/embedding-disabled/LLM-disabled stages, no network model download."
