#!/usr/bin/env bash
set -euo pipefail

# Controlled smoke test. It never creates credentials and requires a caller
# supplied JWT with the ingestion role. Use a disposable/test realm account.
: "${BOOKRUSH_INGESTION_URL:=http://127.0.0.1:18090}"
: "${BOOKRUSH_ANALYTICS_URL:=http://127.0.0.1:18091}"
: "${BOOKRUSH_BOOK_IDS:=84,1342}"
: "${BOOKRUSH_BEARER_TOKEN:?Set BOOKRUSH_BEARER_TOKEN to a temporary OPERATOR/REVIEWER JWT}"
: "${BOOKRUSH_ANALYTICS_TIMEOUT_SECONDS:=180}"

IFS=, read -r -a ids <<< "$BOOKRUSH_BOOK_IDS"
(( ${#ids[@]} == 2 )) || { echo 'BOOKRUSH_BOOK_IDS must contain exactly two Gutenberg IDs' >&2; exit 2; }
json_ids=$(printf '%s\n' "${ids[@]}" | jq -R . | jq -s .)
key="two-book-pipeline-$(printf '%s' "$BOOKRUSH_BOOK_IDS" | sha256sum | cut -c1-16)"
body=$(jq -cn --argjson ids "$json_ids" '{source:"GUTENBERG",externalIds:$ids,languages:["en"],maxItems:2,dryRun:false,processAssets:true}')

echo '1/3 submitting ingestion job'
response=$(curl -fsS -X POST "$BOOKRUSH_INGESTION_URL/api/admin/v1/ingestion/run" \
  -H "Authorization: Bearer $BOOKRUSH_BEARER_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" --data "$body")
job_id=$(jq -r '.jobId // .id // empty' <<<"$response")
test -n "$job_id" || { echo "$response"; exit 1; }

echo "2/3 waiting ingestion job $job_id"
deadline=$((SECONDS + BOOKRUSH_ANALYTICS_TIMEOUT_SECONDS))
while :; do
  status=$(curl -fsS -H "Authorization: Bearer $BOOKRUSH_BEARER_TOKEN" "$BOOKRUSH_INGESTION_URL/api/admin/v1/ingestion/jobs/$job_id" | tee /tmp/bookrush-ingestion-job.json | jq -r '.status')
  case "$status" in COMPLETED|COMPLETED_WITH_ERRORS) break;; FAILED|CANCELLED) echo "ingestion status=$status"; exit 1;; esac
  (( SECONDS < deadline )) || { echo 'ingestion timeout' >&2; exit 1; }; sleep 2
done
test "$status" = COMPLETED || { echo "ingestion completed with errors" >&2; exit 1; }

mapfile -t versions < <(curl -fsS -H "Authorization: Bearer $BOOKRUSH_BEARER_TOKEN" "$BOOKRUSH_INGESTION_URL/api/admin/v1/ingestion/jobs/$job_id/items" | jq -r '.items[] | .asset_version_id // .assetVersionId // empty')
(( ${#versions[@]} > 0 )) || { echo 'ingestion returned no asset version IDs; inspect job items' >&2; exit 1; }
analytics_body=$(printf '%s\n' "${versions[@]}" | jq -R . | jq -s --arg op "$key" '{operationKey:$op,inputAssetVersionIds:.,configuration:{generatorVersion:"sentence-window-v1"}}')
echo '3/3 submitting analytics job'
curl -fsS -X POST "$BOOKRUSH_ANALYTICS_URL/api/admin/v1/content-analytics/jobs" \
  -H "Authorization: Bearer $BOOKRUSH_BEARER_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $key" --data "$analytics_body" | tee /tmp/bookrush-analytics-job.json
echo
