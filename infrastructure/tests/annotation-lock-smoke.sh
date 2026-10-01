#!/usr/bin/env bash
set -euo pipefail

# Real-stack integration check for the blind annotation lock. It exercises the
# browser-facing HTTP route, Spring/JDBC persistence, and the PostgreSQL row.
# It intentionally requires an explicitly supplied short-lived bearer token;
# no credential is read from or written to the repository.
: "${ANALYTICS_TEST_BEARER:?Set a short-lived Keycloak bearer token}"
: "${ANNOTATION_ITEM_ID:?Set an item id reserved for integration testing}"
: "${ANNOTATION_TEST_SUBJECT:?Set the JWT subject used by the token}"

base_url="${ANALYTICS_BASE_URL:-http://127.0.0.1:18091}"
db_container="${BOOKRUSH_DB_CONTAINER:-bookrush-postgres-1}"
db_user="${POSTGRES_USER:-bookrush}"
db_name="${POSTGRES_DB:-bookrush}"
headers=(-H "Authorization: Bearer ${ANALYTICS_TEST_BEARER}" -H 'Content-Type: application/json' -H 'X-Admin-Client: integration-test')
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

curl_args=(--fail-with-body --silent --show-error --connect-timeout 5 --max-time 20)

curl "${curl_args[@]}" "${headers[@]}" -X POST "$base_url/api/admin/v1/annotation/items/$ANNOTATION_ITEM_ID/first-screen" \
  --data '{"hook":3}' >"$tmp_dir/first.json"
curl "${curl_args[@]}" "${headers[@]}" -X POST "$base_url/api/admin/v1/annotation/items/$ANNOTATION_ITEM_ID/lock" \
  --data '{"dimensions":{"HOOK":3,"CLARITY":3,"AUTHENTICITY":3,"IMPACT":3,"PACING":3,"SELF_CONTAINMENT":3},"confidence":3,"failureTags":[]}' >"$tmp_dir/lock.json"

jq -e '.locked == true and .contextAvailable == true' "$tmp_dir/lock.json" >/dev/null

count="$(docker exec "$db_container" psql -U "$db_user" -d "$db_name" -Atc "SELECT count(*) FROM analytics.annotation_dimension_value d JOIN analytics.annotation a ON a.id=d.annotation_id WHERE a.campaign_item_id='$ANNOTATION_ITEM_ID' AND a.annotator_subject='$ANNOTATION_TEST_SUBJECT' AND a.primary_locked_at IS NOT NULL AND d.stage='FULL_EXCERPT';")"
[[ "$count" == 6 ]] || { echo "expected 6 persisted dimensions, got $count" >&2; exit 1; }

set +e
status="$(curl "${headers[@]}" -X POST -o "$tmp_dir/retry.json" -w '%{http_code}' "$base_url/api/admin/v1/annotation/items/$ANNOTATION_ITEM_ID/lock" \
  --data '{"dimensions":{"HOOK":3,"CLARITY":3,"AUTHENTICITY":3,"IMPACT":3,"PACING":3,"SELF_CONTAINMENT":3},"confidence":3,"failureTags":[]}')"
set -e
[[ "$status" == 409 ]] || { echo "expected retry status 409, got $status" >&2; cat "$tmp_dir/retry.json" >&2; exit 1; }
jq -e '.code == "PRIMARY_ALREADY_LOCKED"' "$tmp_dir/retry.json" >/dev/null
echo "annotation lock integration: PASS (HTTP -> Spring -> PostgreSQL; retry conflict verified)"
