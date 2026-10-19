#!/usr/bin/env bash
set -Eeuo pipefail

db_container="${ANALYTICS_DB_CONTAINER:-bookrush-postgres-1}"
db_user="${POSTGRES_USER:-bookrush}"
db_name="${POSTGRES_DB:-bookrush}"
rounds="${JIT_PAIRED_ROUNDS:-10}"
parallel_rounds="${JIT_PARALLEL_ROUNDS:-5}"
parallelism="${JIT_PARALLELISM:-4}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="${JIT_BENCHMARK_OUTPUT_DIR:-docs/operations/incidents/evidence/raw/feed-benchmark-$stamp/jit-repeat}"
mkdir -p "$out/raw" "$out/parallel"
results="$out/results.tsv"
parallel_results="$out/parallel-results.tsv"
printf 'round\torder\tmode\texecution_ms\tjit_ms\trows\n' >"$results"
printf 'round\tmode\tworker\texecution_ms\tjit_ms\trows\n' >"$parallel_results"

query="$(cat <<'SQL'
WITH requested AS (
  SELECT id AS book_id
    FROM catalog.book
   ORDER BY canonical_title, id
   LIMIT 100
), latest_asset AS (
  SELECT DISTINCT ON (a.book_id) a.book_id, v.id AS version_id
    FROM catalog.book_asset a
    JOIN catalog.book_asset_version v ON v.id = a.current_version_id
   WHERE a.book_id IN (SELECT book_id FROM requested)
     AND a.asset_role IN ('NORMALIZED', 'PROCESSING', 'ANALYTICS')
     AND a.asset_type IN ('TXT', 'HTML')
     AND a.status <> 'DELETED'
     AND v.status = 'AVAILABLE'
   ORDER BY a.book_id, a.updated_at DESC, a.id DESC
), ranked AS (
  SELECT la.book_id, er.final_score, er.ranker_version, er.created_at
    FROM latest_asset la
    JOIN analytics.excerpt e ON e.source_asset_version_id = la.version_id
    JOIN LATERAL (
      SELECT final_score, ranker_version, created_at
        FROM analytics.excerpt_rank
       WHERE excerpt_id = e.id
         AND final_score IS NOT NULL
       ORDER BY created_at DESC
       LIMIT 1
    ) er ON TRUE
   WHERE e.body_eligible = TRUE AND e.exclusion_reason IS NULL
)
SELECT book_id, MAX(final_score) AS score,
       (ARRAY_AGG(ranker_version ORDER BY final_score DESC, created_at DESC))[1] AS ranker_version
  FROM ranked
 GROUP BY book_id
SQL
)"

run_explain() {
  local mode="$1" output="$2"
  docker exec -i "$db_container" psql -X -qAt -v ON_ERROR_STOP=1 \
    -U "$db_user" -d "$db_name" \
    >"$output" <<SQL
BEGIN;
SET LOCAL jit = '$mode';
EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)
$query;
ROLLBACK;
SQL
}

parse_explain() {
  local raw="$1" round="$2" order="$3" mode="$4" destination="$5"
  python3 - "$raw" "$round" "$order" "$mode" "$destination" <<'PY'
import json
import sys

raw, round_no, order, mode, destination = sys.argv[1:]
text = open(raw, encoding="utf-8").read().strip()
payload = json.loads(text)
plan = payload[0]['Plan']
execution = float(payload[0]['Execution Time'])
jit = payload[0].get('JIT', {}).get('Timing', {}).get('Total', 0.0)
rows = int(plan.get('Actual Rows', 0))
with open(destination, 'a', encoding='utf-8') as handle:
    handle.write(f'{round_no}\t{order}\t{mode}\t{execution:.3f}\t{float(jit):.3f}\t{rows}\n')
PY
}

for round_no in $(seq 1 "$rounds"); do
  if (( round_no % 2 == 1 )); then order="on-off"; else order="off-on"; fi
  for mode in ${order//-/ }; do
    raw="$out/raw/sequential-${round_no}-${mode}.json"
    run_explain "$mode" "$raw"
    parse_explain "$raw" "$round_no" "$order" "$mode" "$results"
  done
done

for round_no in $(seq 1 "$parallel_rounds"); do
  for mode in on off; do
    pids=()
    for worker_no in $(seq 1 "$parallelism"); do
      raw="$out/parallel/${round_no}-${mode}-${worker_no}.json"
      run_explain "$mode" "$raw" &
      pids+=("$!")
    done
    for pid in "${pids[@]}"; do wait "$pid"; done
    for worker_no in $(seq 1 "$parallelism"); do
      raw="$out/parallel/${round_no}-${mode}-${worker_no}.json"
      python3 - "$raw" "$round_no" "$mode" "$worker_no" "$parallel_results" <<'PY'
import json
import sys

raw, round_no, mode, worker_no, destination = sys.argv[1:]
payload = json.loads(open(raw, encoding='utf-8').read().strip())
plan = payload[0]['Plan']
execution = float(payload[0]['Execution Time'])
jit = payload[0].get('JIT', {}).get('Timing', {}).get('Total', 0.0)
rows = int(plan.get('Actual Rows', 0))
with open(destination, 'a', encoding='utf-8') as handle:
    handle.write(f'{round_no}\t{mode}\t{worker_no}\t{execution:.3f}\t{float(jit):.3f}\t{rows}\n')
PY
    done
  done
done

printf 'output=%s\nresults=%s\nparallel_results=%s\n' "$out" "$results" "$parallel_results"
