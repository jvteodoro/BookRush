#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
validation_only=false
case "${1:-}" in
  "") ;;
  --validation-only) validation_only=true ;;
  *) echo "Uso: $0 [--validation-only]" >&2; exit 2 ;;
esac

# The validation image is never run. cacheonly preserves BuildKit cache for a
# possible runtime build without spending minutes exporting/unpacking layers.
docker build -f backstage/Dockerfile --target validation --output=type=cacheonly .

if ! $validation_only; then
  docker build -f backstage/Dockerfile --target runtime -t bookrush/portal:local .
fi
