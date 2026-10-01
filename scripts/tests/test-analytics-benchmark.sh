#!/usr/bin/env bash
set -euo pipefail
tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
printf 'a\nb\nc\n' > "$tmp"
out=$(python3 scripts/benchmark-analytics.py --input "$tmp" --iterations 2)
python3 -c 'import json,sys; x=json.loads(sys.argv[1]); assert x["records"]==6 and x["model_download"] is False' "$out"
echo 'analytics benchmark smoke passed'
