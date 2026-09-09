#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-db-test.XXXXXXXX")"
project="$(basename "$run_dir" | tr '[:upper:].' '[:lower:]-')"
compose=(docker compose --project-name "$project" -f "$repo_dir/infrastructure/compose.database-test.yaml")
cleanup() {
    local status=$?
    trap - EXIT
    "${compose[@]}" down --volumes --remove-orphans || true
    rmdir -- "$run_dir"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${compose[@]}" up --build --abort-on-container-exit --exit-code-from tests tests
