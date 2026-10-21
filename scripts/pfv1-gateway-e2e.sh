#!/usr/bin/env bash
set -euo pipefail

# Authenticated smoke/E2E harness. Tokens are supplied through the environment
# and are never printed or persisted.
: "${BOOKRUSH_BASE_URL:?BOOKRUSH_BASE_URL is required}"
: "${PFV1_USER_A_TOKEN:?PFV1_USER_A_TOKEN is required}"
: "${PFV1_USER_B_TOKEN:?PFV1_USER_B_TOKEN is required}"
: "${PFV1_COLD_TOKEN:?PFV1_COLD_TOKEN is required}"

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

fetch() {
  local name="$1" token="$2"
  curl --fail-with-body --silent --show-error \
    --header "Authorization: Bearer ${token}" \
    --header 'Accept: application/json' \
    "${BOOKRUSH_BASE_URL}/api/v1/reader/feed?size=10" > "${workdir}/${name}.json"
}

fetch user-a "$PFV1_USER_A_TOKEN"
fetch user-b "$PFV1_USER_B_TOKEN"
fetch cold "$PFV1_COLD_TOKEN"

for name in user-a user-b cold; do
  jq -e '.items | type == "array"' "${workdir}/${name}.json" >/dev/null
  jq -e '.personalization.path and .candidateSetHash and (.items | all(.[]; has("recommendationRequestId")))' \
    "${workdir}/${name}.json" >/dev/null
done

# The harness proves the gateway contract and provenance. Rank divergence is a
# data-dependent assertion and can be enabled for a controlled fixture.
if [[ "${PFV1_EXPECT_DIFFERENT_TOP1:-false}" == true ]]; then
  a="$(jq -r '.items[0].book.id // empty' "${workdir}/user-a.json")"
  b="$(jq -r '.items[0].book.id // empty' "${workdir}/user-b.json")"
  [[ -n "$a" && -n "$b" && "$a" != "$b" ]] || {
    echo "two-user fixture did not produce different top-1 books" >&2
    exit 1
  }
fi

echo "PFV1 gateway contract smoke passed (three authenticated subjects; tokens withheld)."
