#!/usr/bin/env bash
set -euo pipefail

# Usage: DATABASE_URL=postgresql://... ./export-pfv1-dataset.sh 2026-10-20T23:59:59Z output.csv
# The caller must use a read-only database snapshot. No credentials are logged.
cutoff="${1:?cutoff timestamp is required}"
output="${2:?output CSV path is required}"
: "${DATABASE_URL:?DATABASE_URL is required}"

mkdir -p "$(dirname "$output")"
psql "$DATABASE_URL" --set=counter=0 --set=cutoff="$cutoff" \
  --file="$(dirname "$0")/export-pfv1-dataset.sql" > "$output"

sha256sum "$output" | awk '{print "dataset_sha256=" $1}'
awk 'END {print "row_count=" (NR > 0 ? NR - 1 : 0)}' "$output"
