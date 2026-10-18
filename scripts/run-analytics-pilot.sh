#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
image=${ANALYTICS_RUNTIME_TEST_IMAGE:-bookrush/book-analytics-runtime:validation}
volume=${ANALYTICS_MODEL_VOLUME:-bookrush_analytics_models}
port=${ANALYTICS_PILOT_PORT:-18093}
container="bookrush-analytics-pilot-$$"
output=${ANALYTICS_PILOT_OUTPUT:-"$root_dir/.analytics-pilot-report.json"}

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker build -t "$image" "$root_dir/services/book-analytics-runtime" >/dev/null
docker run -d --rm --name "$container" -p "${port}:8092" -e ANALYTICS_OFFLINE=true \
  -v "${volume}:/var/lib/bookrush/models:ro" "$image" >/dev/null
for _ in $(seq 1 120); do
  if curl -fsS "http://127.0.0.1:${port}/health" >/dev/null; then break; fi
  sleep 1
done
python3 "$root_dir/scripts/run-analytics-pilot.py" --base-url "http://127.0.0.1:${port}" --output "$output"
