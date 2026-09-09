#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
run_dir="$(mktemp -d "${TMPDIR:-/tmp}/bookrush-storage-test.XXXXXXXX")"
project="$(basename "$run_dir" | tr '[:upper:].' '[:lower:]-')"
compose=(docker compose --project-name "$project" -f "$repo_dir/infrastructure/compose.storage-test.yaml")
cleanup() {
  local status=$?
  trap - EXIT
  "${compose[@]}" down --volumes --remove-orphans || true
  if [[ -n "${backup_volume:-}" ]]; then docker volume rm "$backup_volume" || true; fi
  # Only known temporary artifacts from this invocation.
  for file in persistence-url probe-before probe-after postgres.dump; do
    if [[ -f "$run_dir/$file" ]]; then unlink "$run_dir/$file"; fi
  done
  rmdir "$run_dir"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${compose[@]}" up -d --build --wait --wait-timeout 180 postgres seaweedfs
# Preserve the dynamically allocated port when recreating SeaweedFS.
STORAGE_TEST_PORT="$("${compose[@]}" port seaweedfs 8333 | awk -F: '{print $NF}')"
export STORAGE_TEST_PORT
export STORAGE_HOST_ENDPOINT="http://localhost:$STORAGE_TEST_PORT"
"${compose[@]}" build tests
"${compose[@]}" create tests
test_container="$("${compose[@]}" ps -aq tests)"
# docker cp works with Jenkins's host socket; no bind from an inaccessible workspace.
docker cp "$repo_dir/docs/database/queries.sql" "$test_container:/workspace/documented-queries.sql"
"${compose[@]}" start tests
result="$(docker wait "$test_container")"
docker logs "$test_container"
test_image="$(docker inspect --format '{{.Config.Image}}' "$test_container")"
if [[ "$result" != 0 ]]; then exit "$result"; fi
# Integration test leaves a single canary and its host-signed URL (test credentials only).
docker cp "$test_container:/workspace/persistence-url" "$run_dir/persistence-url"
# curl config avoids placing the signed capability in logs or process arguments.
docker run --rm --network host -i --entrypoint curl "$test_image" --fail --silent --show-error --config - < "$run_dir/persistence-url" > "$run_dir/probe-before"
"${compose[@]}" up -d --no-deps --force-recreate --wait --wait-timeout 180 seaweedfs
docker run --rm --network host -i --entrypoint curl "$test_image" --fail --silent --show-error --retry 10 --retry-all-errors --retry-delay 1 --config - < "$run_dir/persistence-url" > "$run_dir/probe-after"
cmp "$run_dir/probe-before" "$run_dir/probe-after"
echo "External presigned GET and persistence after recreation: OK"


# Consistent snapshot with no writers: test process has exited, stop storage for physical copy.
weed_container="$("${compose[@]}" ps -q seaweedfs)"
postgres_container="$("${compose[@]}" ps -q postgres)"
docker exec "$postgres_container" pg_dump -U bookrush_test -d bookrush_test -Fc > "$run_dir/postgres.dump"
docker exec "$postgres_container" createdb -U bookrush_test restore_probe
docker exec -i "$postgres_container" pg_restore -U bookrush_test -d restore_probe < "$run_dir/postgres.dump"
docker exec "$postgres_container" psql -U bookrush_test -d restore_probe -Atc "SELECT count(*) FROM catalog.book_asset_version" | awk '$1 > 0 {ok=1} END {exit !ok}'
"${compose[@]}" stop seaweedfs
backup_volume="${project}_backup"
docker volume create "$backup_volume" > /dev/null
docker run --rm --network none --volumes-from "$weed_container":ro --mount "type=volume,src=$backup_volume,dst=/backup" --entrypoint sh "$test_image" -c 'cp -a /data/. /backup/'
# Restore into the test volume from snapshot after removing the test container.
# This volume is exclusively created by this invocation, never an application volume.
data_volume="$(docker inspect "$weed_container" --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}')"
"${compose[@]}" rm -f seaweedfs
docker volume rm "$data_volume" > /dev/null
docker volume create "$data_volume" > /dev/null
docker run --rm --network none --mount "type=volume,src=$backup_volume,dst=/backup,readonly" --mount "type=volume,src=$data_volume,dst=/data" --entrypoint sh "$test_image" -c 'cp -a /backup/. /data/'
"${compose[@]}" up -d --no-deps --wait --wait-timeout 180 seaweedfs
docker run --rm --network host -i --entrypoint curl "$test_image" --fail --silent --show-error --retry 10 --retry-all-errors --retry-delay 1 --config - < "$run_dir/persistence-url" > "$run_dir/probe-after"
cmp "$run_dir/probe-before" "$run_dir/probe-after"
echo "PostgreSQL logical restore and SeaweedFS physical restore: OK"
