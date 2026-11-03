#!/usr/bin/env bash
set -euo pipefail

# Usage: DATABASE_URL=postgresql://recommendation/... BEHAVIOR_DATABASE_URL=postgresql://behavior/... ./export-pfv1-dataset.sh 2026-10-20T23:59:59Z output.csv
# The caller must use a read-only database snapshot. No credentials are logged.
cutoff="${1:?cutoff timestamp is required}"
output="${2:?output CSV path is required}"
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${BEHAVIOR_DATABASE_URL:?BEHAVIOR_DATABASE_URL is required}"

mkdir -p "$(dirname "$output")"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
psql "$DATABASE_URL" --set=counter=0 --set=cutoff="$cutoff" \
  --file="$(dirname "$0")/export-pfv1-dataset.sql" > "$temp_dir/decisions.csv"
psql "$BEHAVIOR_DATABASE_URL" --set=cutoff="$cutoff" \
  --file="$(dirname "$0")/export-pfv1-outcomes.sql" > "$temp_dir/outcomes.csv"
python3 "$(dirname "$0")/join-pfv1-dataset.py" \
  "$temp_dir/decisions.csv" "$temp_dir/outcomes.csv" "$cutoff" "$output"

sha256sum "$output" | awk '{print "dataset_sha256=" $1}'
awk 'END {print "row_count=" (NR > 0 ? NR - 1 : 0)}' "$output"
