#!/usr/bin/env bash
set -Eeuo pipefail
root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
printf 'fixture-model' > "$work_dir/input.bin"
hash=$(sha256sum "$work_dir/input.bin" | awk '{print $1}')
mkdir -p "$work_dir/cache/fixture-model"
cp "$work_dir/input.bin" "$work_dir/cache/fixture-model/input.bin"
cat > "$work_dir/manifest.json" <<JSON
{"schemaVersion":"bookrush.analytics.model-artifacts.v1","artifacts":[{"id":"fixture-model","license":"Test","source":{"type":"url","url":"https://invalid.example/fixture"},"files":[{"path":"input.bin","sha256":"$hash"}]}]}
JSON
ANALYTICS_MODEL_CACHE_DIR="$work_dir/cache" ANALYTICS_MODEL_MANIFEST="$work_dir/manifest.json" \
  bash "$root_dir/scripts/fetch-analytics-models.sh" --verify >/dev/null
printf 'corruption' >> "$work_dir/cache/fixture-model/input.bin"
if ANALYTICS_MODEL_CACHE_DIR="$work_dir/cache" ANALYTICS_MODEL_MANIFEST="$work_dir/manifest.json" \
  bash "$root_dir/scripts/fetch-analytics-models.sh" --verify >/dev/null 2>&1; then
  echo 'corrupt artifact unexpectedly verified' >&2
  exit 1
fi
echo 'analytics model artifact lock test passed'
