#!/usr/bin/env bash
set -euo pipefail

root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT
cd "$root_dir"

python3 - "$tmp_dir/review.jsonl" "$tmp_dir/compare.jsonl" <<'PY'
import json, sys
review, compare = sys.argv[1:]
catalog = json.load(open("services/book-analytics-service/src/main/resources/nli-hypotheses-v1.json", encoding="utf-8"))
with open(review, "w", encoding="utf-8") as out:
    for language in ("en", "pt"):
        labels = {kind: {item["key"]: 3 for item in catalog["sets"][language] if item["kind"] == kind}
                  for kind in ("narrative", "emotion")}
        out.write(json.dumps({"id": language, "language": language, "text": "A sufficiently long review fixture passage " * 12,
                              "source": "fixture", "reviewer": "fixture-reviewer", "labels": labels}, ensure_ascii=False) + "\n")
with open(compare, "w", encoding="utf-8") as out:
    out.write(json.dumps({"id": "pt-1", "key": "conflict", "pt_entailment": .7, "en_entailment": .6}) + "\n")
PY
python3 scripts/validate-nli-review-set.py "$tmp_dir/review.jsonl" --min-per-language 1 >/dev/null
python3 scripts/compare-nli-language-hypotheses.py "$tmp_dir/compare.jsonl" >/dev/null
echo "NLI validation tools passed"
