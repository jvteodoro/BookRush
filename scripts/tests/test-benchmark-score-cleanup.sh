#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cp "$root/scripts/tests/fixtures/fake-docker-for-score-benchmark" "$tmp/bin/docker"
chmod +x "$tmp/bin/docker"

run_case() {
  local initial="$1" expected_state="$2" expected_pattern="$3"
  printf '%s\n' "$initial" > "$tmp/state"
  : > "$tmp/docker.log"
  PATH="$tmp/bin:$PATH" FAKE_DOCKER_STATE="$tmp/state" FAKE_DOCKER_LOG="$tmp/docker.log" \
    JIT_PAIRED_ROUNDS=0 JIT_PARALLEL_ROUNDS=0 JIT_BENCHMARK_OUTPUT_DIR="$tmp/output" \
    SCORE_INDEXES=present ALLOW_EXPERIMENTAL_DDL=true \
    bash "$root/scripts/benchmark-score-jit.sh" >/dev/null
  diff -u <(printf '%s\n' "$expected_state") "$tmp/state"
  if [[ -n "$expected_pattern" ]]; then
    ! grep -q "$expected_pattern" "$tmp/docker.log"
  fi
}

# Newly created experimental indexes are the only objects the trap may remove.
run_case "" "" "DROP INDEX CONCURRENTLY IF EXISTS analytics.excerpt_rank_latest_score_idx"

# A definitive Flyway index suppresses a redundant experiment and is untouched.
run_case "def_rank" "def_rank" "DROP INDEX CONCURRENTLY IF EXISTS analytics.excerpt_rank_latest_score_idx"

# An experimental index present before the run is also preserved.
run_case $'exp_rank\nexp_excerpt' $'exp_rank\nexp_excerpt' "DROP INDEX CONCURRENTLY IF EXISTS analytics.feed_score_rank_latest_experiment_idx"

printf '%s\n' 'score benchmark cleanup tests passed'
