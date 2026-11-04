#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_DIR="$ROOT_DIR/scripts/fixtures"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

python3 "$ROOT_DIR/scripts/join-pfv1-dataset.py" \
  "$FIXTURE_DIR/pfv1-decisions.csv" \
  "$FIXTURE_DIR/pfv1-outcomes.csv" \
  "2026-11-04T00:00:00Z" \
  "$TMP_DIR/output.csv"

python3 - "$TMP_DIR/output.csv" <<'PY'
import csv
import sys

with open(sys.argv[1], newline="") as stream:
    rows = list(csv.DictReader(stream))
assert len(rows) == 1
row = rows[0]
for label in ("dwell", "meaningful_read", "read_progress", "read_10", "read_50", "complete"):
    assert row[label] == "1", (label, row)
print("PFV1 dataset fixture: OK")
PY
