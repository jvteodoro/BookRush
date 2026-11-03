#!/usr/bin/env bash
set -euo pipefail
: "${BOOKRUSH_ANALYTICS_URL:=http://book-analytics-service:8091}"
: "${BOOKRUSH_BEARER_TOKEN:?Set BOOKRUSH_BEARER_TOKEN}"
: "${BOOKRUSH_POSTGRES_CONTAINER:=bookrush-postgres-1}"
: "${ANALYTICS_SAMPLE_MAX_BOOKS:=4}"
: "${ANALYTICS_SAMPLE_TIMEOUT_SECONDS:=1800}"
auth=(-H "Authorization: Bearer $BOOKRUSH_BEARER_TOKEN")
api="${BOOKRUSH_ANALYTICS_URL%/}/api/admin/v1/content-analytics"
active="$(for status in PENDING RUNNING; do curl -fsS "${api}/jobs?status=${status}&limit=200" "${auth[@]}" | jq -r '.items[]?.id'; done | sort -u)"
while read -r job_id; do
  test -n "$job_id" || continue
  curl -fsS -X POST "${api}/jobs/${job_id}/cancel" "${auth[@]}" >/dev/null
  echo "analytics_cancel_requested=$job_id"
done <<< "$active"
deadline=$((SECONDS + 120))
while :; do
  remaining=0
  for status in PENDING RUNNING; do
    count=$(curl -fsS "${api}/jobs?status=${status}&limit=200" "${auth[@]}" | jq '.items | length')
    remaining=$((remaining + count))
  done
  (( remaining == 0 )) && break
  (( SECONDS < deadline )) || { echo 'analytics cancellation timeout' >&2; exit 1; }
  sleep 5
done
mapfile -t asset_versions < <(docker exec "$BOOKRUSH_POSTGRES_CONTAINER" psql -U bookrush -d bookrush -Atc "SELECT DISTINCT ON (b.id) v.id FROM catalog.book b JOIN catalog.book_asset a ON a.book_id=b.id JOIN catalog.book_asset_version v ON v.book_asset_id=a.id WHERE b.status IN ('ACTIVE','REVIEW_REQUIRED') AND a.asset_role IN ('NORMALIZED','PROCESSING','ANALYTICS') ORDER BY b.id, v.created_at DESC LIMIT ${ANALYTICS_SAMPLE_MAX_BOOKS}")
(( ${#asset_versions[@]} > 0 )) || { echo 'no eligible asset versions' >&2; exit 1; }
operation_key="pfv1-score-sample-$(date -u +%Y%m%dT%H%M%SZ)"
ids=$(printf '%s\n' "${asset_versions[@]}" | jq -R . | jq -s .)
body=$(jq -cn --arg key "$operation_key" --argjson ids "$ids" '{operationKey:$key,inputAssetVersionIds:$ids,configuration:{generatorVersion:"sentence-window-v1",purpose:"pfv1-feed-score-sample"}}')
response=$(curl -fsS -X POST "${api}/jobs" "${auth[@]}" -H 'Content-Type: application/json' -H "Idempotency-Key: $operation_key" --data "$body")
job_id=$(jq -r '.id // empty' <<<"$response")
test -n "$job_id" || { echo 'analytics job creation returned no id' >&2; exit 1; }
echo "analytics_sample_job=$job_id items=${#asset_versions[@]}"
deadline=$((SECONDS + ANALYTICS_SAMPLE_TIMEOUT_SECONDS))
while :; do
  status=$(curl -fsS "${api}/jobs/${job_id}" "${auth[@]}" | jq -r '.status')
  echo "analytics_sample_status=$status"
  case "$status" in COMPLETED) exit 0 ;; FAILED|CANCELLED|COMPLETED_WITH_ERRORS) exit 1 ;; esac
  (( SECONDS < deadline )) || { echo 'analytics sample timeout' >&2; exit 1; }
  sleep 10
done
