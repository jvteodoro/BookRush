#!/usr/bin/env bash
set -euo pipefail
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
printf 'Casa e livro. Casa.' > "$tmp/pt.txt"
printf '%s\n' "$tmp/pt.txt" > "$tmp/manifest"
python3 scripts/build-corpus-frequency.py --language pt --snapshot fixture-v2 --manifest "$tmp/manifest" --output "$tmp/artifact.json" >/dev/null
python3 - "$tmp/artifact.json" <<'PY'
import json,sys
x=json.load(open(sys.argv[1])); assert x['language']=='pt' and x['token_count']==4 and x['counts']['casa']==2 and x['artifact_sha256']
PY
echo 'corpus frequency smoke passed'
