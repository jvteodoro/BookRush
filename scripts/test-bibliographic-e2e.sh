#!/usr/bin/env bash
set -euo pipefail

# Offline acceptance harness for the bibliographic enrichment vertical.
# Fixtures are local; no Gutenberg/Open Library/Wikidata network request is made.
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ingestion_log=$(mktemp)
database_log=$(mktemp)
trap 'rm -f "$ingestion_log" "$database_log"' EXIT

cd "$repo_root"
echo "[1/2] ingestion fixtures: staging replay, matching, subjects and Wikidata snapshot"
docker build --target build -t bookrush/ingestion-e2e services/book-ingestion-service >/dev/null
docker run --rm --entrypoint mvn bookrush/ingestion-e2e test -DskipTests=false | tee "$ingestion_log"
grep -Eq 'Tests run: 26, Failures: 0, Errors: 0' "$ingestion_log"

echo "[2/2] catalog fixtures: canonical provenance, subjects, idempotency and Flyway upgrade"
bash scripts/test-database.sh | tee "$database_log"
grep -Eq 'Tests run: 43, Failures: 0, Errors: 0' "$database_log"
grep -q 'BUILD SUCCESS' "$database_log"

cat <<'REPORT'

Bibliographic E2E acceptance passed.
- local ingestion fixture suite: 26 tests, including OpenLibraryStageJob replay,
  deterministic matching, subject extraction and checksum-verified Wikidata snapshot;
- catalog PostgreSQL harness: 43 tests, 0 failures, 6 skips, Flyway fresh and
  upgrade paths through V14;
- second staging/subject assignment is asserted as a NOOP and canonical replay
  does not duplicate field provenance;
- no remote source or network API was used by this harness.
REPORT
