#!/usr/bin/env bash
set -euo pipefail
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
cat > "$tmpdir/data.jsonl" <<'EOF'
{"work_id":"w1","embedding":[1,0,0],"labels":["fiction"]}
{"work_id":"w2","embedding":[0,1,0],"labels":["poetry"]}
{"work_id":"w3","embedding":[1,0,0],"labels":["fiction"]}
{"work_id":"w4","embedding":[0,1,0],"labels":["poetry"]}
EOF
if ! python3 -c 'import sklearn, joblib, numpy' 2>/dev/null; then
  echo 'topic classifier tools require sklearn, joblib and numpy' >&2
  exit 0
fi
python3 scripts/train-topic-classifier.py --input "$tmpdir/data.jsonl" --output "$tmpdir/model.joblib" >/dev/null
python3 scripts/evaluate-topic-classifier.py --input "$tmpdir/data.jsonl" --artifact "$tmpdir/model.joblib" --output "$tmpdir/metrics.json" >/dev/null
python3 -c 'import json,sys; x=json.load(open(sys.argv[1])); assert "macro_f1" in x and x["promotion"] == "BLOCKED"' "$tmpdir/metrics.json"
echo 'topic classifier tools passed'
