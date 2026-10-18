#!/usr/bin/env bash
set -euo pipefail
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/input.jsonl" <<'EOF'
{"author_id":"a1","work_id":"w1","features":{"hook":1,"autonomy":2},"labels":{"hook":4,"curiosity":2}}
{"author_id":"a2","work_id":"w2","features":{"hook":3,"autonomy":1},"labels":{"hook":3,"curiosity":4}}
{"author_id":"a3","work_id":"w3","features":{"hook":2,"autonomy":4},"labels":{"hook":5,"curiosity":3}}
{"author_id":"a4","work_id":"w4","features":{"hook":4,"autonomy":3},"labels":{"hook":4,"curiosity":5}}
EOF
python3 scripts/train-candidate-scorer.py --input "$tmp/input.jsonl" --output "$tmp/artifact.json" >/dev/null
python3 - "$tmp/artifact.json" <<'PY'
import json,sys
x=json.load(open(sys.argv[1])); assert x['code']=='EXCERPT_SUPERVISED_SCORER_V1'; assert x['version']=='1.1.0'; assert x['split']['group_count']==4; assert x['split']['group_key']=='author_id::work_id'; assert set(x['models'])=={'hook','curiosity'}; assert x['artifact_sha256']
PY
echo 'candidate scorer smoke passed'
