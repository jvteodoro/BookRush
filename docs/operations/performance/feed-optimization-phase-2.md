# Feed optimization phase 2

Status: experimental, branch `feature/feed-optimization-phase-2`.

This phase turns the 2026-10-19 characterization into a reproducible code
path. It does not deploy SQL indexes, change PostgreSQL global settings, or
materialize scores.

## Audit baseline

The reference implementation at `ebeddddd31499079c8ec710fd14216129ed8bf90`
had the batch score SQL embedded in
`services/book-analytics-service/.../AnalyticsController.java`. It selected
the current catalog asset, filtered eligible excerpts, and used a lateral
lookup for the newest non-null rank before taking the maximum score per book.
`AnalyticsScoreClient` already had a bounded per-book cache and a 2-second
read timeout, but only exposed aggregate result counters. The feed controller
performed history, catalog, score, ranking, event, excerpt, and ledger work
without stage timers.

Existing analytics metrics use low-cardinality names under
`bookrush.analytics.*`; recommendation uses Micrometer through the shared
observability starter. The analytics service uses PostgreSQL/JDBC and Flyway.
No production migration is introduced here.

## Implementation

### Selectable score SQL

`BookScoresQueryRepository` separates SQL from the HTTP controller and offers:

- `CURRENT`: the existing query shape, now the default;
- `OPTIMIZED`: first restricts the relation to requested books and their
  current eligible asset versions, then performs the latest-rank lookup only
  for those excerpts.

Configuration is explicit and safe by default:

```yaml
ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT|OPTIMIZED
ANALYTICS_BOOK_SCORES_JIT=DEFAULT|ON|OFF
```

`ON` and `OFF` execute `SET LOCAL jit` inside a real JDBC transaction on the
same connection as the score query. `DEFAULT` sends no setting. No connection
pool or PostgreSQL global setting is changed.

The response contract remains `{items, requested, returned}` and the selected
rank is still the newest non-null rank for each eligible excerpt, followed by
the greatest score per book. Duplicate request IDs are deduplicated by the
controller before SQL execution.

### Experimental indexes

The candidate indexes are in
[`scripts/experimental/feed-score-indexes.sql`](../../../scripts/experimental/feed-score-indexes.sql),
with rollback in `drop-feed-score-indexes.sql`. They are intentionally not
Flyway migrations and must only be run against a disposable/staging copy.
The scripts use `CREATE/DROP INDEX CONCURRENTLY`, so they must run outside an
explicit transaction. No DDL was executed in production by this phase.

### Feed observability

`FeedMetrics` records low-cardinality timers under
`bookrush.feed.stage.duration` with `stage` values:

`history_count`, `catalog_fetch`, `analytics_scores`, `ranking`,
`behavior_publish`, `excerpt_enrichment`, `ledger_persist`, and `total`.

Counters expose candidate, score, selected, item, excerpt, empty-feed, cache
hit/miss, timeout/error and returned-score counts. IDs, subjects, tokens and
payloads are never metric tags. The analytics endpoint additionally records
SQL duration, endpoint duration, strategy, JIT mode, requested rows and
returned rows. Hikari metrics remain available through the existing actuator
configuration.

### Reproducible benchmark

`scripts/benchmark-feed-performance.sh` was extended without changing its
scenario model. It now records `metadata.json` (commit, explicit strategy/JIT
settings and sample sizes) and calculates `summary.tsv` with mean, standard
deviation, p50, p95, p99, throughput, error rate and mean items. It no longer
uses `curl -k`; configure the host trust chain or `CURL_CA_BUNDLE` in the
authorized environment. The existing raw request TSV remains the source of
truth.

Run only in an authorized environment with a fresh token supplied through the
protected env file:

```bash
ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT \
ANALYTICS_BOOK_SCORES_JIT=DEFAULT \
SEQUENTIAL_REPS=15 CONCURRENCY=4 PARALLEL_REPS=5 PAUSED_REPS=15 \
BENCHMARK_OUTPUT_DIR=/tmp/feed-phase-2-current \
bash scripts/benchmark-feed-performance.sh
```

Run the same population again with `OPTIMIZED`, and compare `summary.tsv`.
For SQL-only plans, use the existing `scripts/benchmark-score-jit.sh` and
capture `EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)` before/after the
experimental indexes. Alternate configuration order, warm up explicitly,
and do not call HTTP 200 with `items=[]` a functional success when scores are
expected.

## Automated validation

`BookScoresQueryRepositoryIT` uses a disposable PostgreSQL 16 Testcontainer,
creates only the relevant catalog/analytics tables, inserts multiple assets,
eligible/ineligible excerpts and multiple rank versions, and asserts that
`CURRENT` and `OPTIMIZED` return the same rows. It also exercises `SET LOCAL
jit=off` and verifies that the connection's global setting remains available
after the transaction.

The IT is opt-in (`RUN_SQL_INTEGRATION=true`) because the regular Jenkins
service test container does not necessarily expose a Docker daemon. In this
workspace the explicit attempt was blocked by the embedded Docker Java client
negotiating API 1.32 while the daemon requires 1.40; this is an environment
limitation, not a passing SQL integration result.

Commands:

```bash
docker build --target build -t bookrush/book-analytics-service:feed-optimization-test \
  services/book-analytics-service
docker run --rm bookrush/book-analytics-service:feed-optimization-test mvn -B test

docker run --rm -e RUN_SQL_INTEGRATION=true \
  -v /var/run/docker.sock:/var/run/docker.sock \
  bookrush/book-analytics-service:feed-optimization-test \
  mvn -B -Dtest=BookScoresQueryRepositoryIT test

docker build --target build -t bookrush/recommendation-service:feed-optimization-test \
  -f services/recommendation-service/Dockerfile .
docker run --rm bookrush/recommendation-service:feed-optimization-test mvn -B test

bash -n scripts/benchmark-feed-performance.sh scripts/benchmark-score-jit.sh \
  scripts/experimental/*.sql 2>/dev/null || true
```

SQL files are not Bash and should be validated with `psql` in the disposable
database, not with `bash -n`; the last command is only suitable for the shell
scripts. The service builds in this phase passed: analytics 93 tests and
recommendation 12 tests.

## Current evidence and decision

The production measurements in
[`feed-characterization-2026-10-19.md`](feed-characterization-2026-10-19.md)
remain the baseline. They showed JIT-off and candidate indexes were promising
in a disposable 94k-excerpt database, but the optimized query alone was not
consistently better in production without indexes. This phase therefore keeps
`CURRENT`/`DEFAULT` as the default and does not claim an optimization win.

The final adoption decision requires running the new harness against a
representative staging snapshot with the four SQL/JIT combinations, absent and
present experimental indexes, concurrency 1/4/8, sizes 10/25/50, cold/hot
cache, and a controlled active/paused analytics runtime. Record image digest,
commit, dataset cardinalities and p50/p95/p99 in a follow-up report.

## Rollback

1. Set `ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT` and
   `ANALYTICS_BOOK_SCORES_JIT=DEFAULT`.
2. Restart only the analytics service after a reviewed configuration deploy;
   no data migration is required.
3. If experimental indexes were created in staging, run
   `scripts/experimental/drop-feed-score-indexes.sql` outside a transaction.
4. Do not change production PostgreSQL settings or run the scripts against the
   production database as part of this phase.
