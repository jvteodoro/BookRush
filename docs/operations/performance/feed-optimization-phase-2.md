# Feed optimization phase 2

Status: definitive index implementation prepared for review, branch
`feature/feed-optimization-phase-2`.

This document records the characterization and the follow-up implementation.
The code now includes the definitive Flyway migrations, but those migrations
have not been applied to the production database. PostgreSQL global settings
and score materialization remain unchanged.

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
The definitive migration path uses Flyway 10.10 per-script
`executeInTransaction=false` configuration and a PostgreSQL session-level
Flyway lock (`flyway.postgresql.transactional.lock=false`). The latter is
required because `CREATE INDEX CONCURRENTLY` cannot be used while Flyway holds
the default transactional advisory lock.

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

The candidate indexes remain available in
`scripts/experimental/feed-score-indexes.sql`,
with rollback in `drop-feed-score-indexes.sql`. They are intentionally not
Flyway migrations and must only be run against a disposable/staging copy.
The scripts use `CREATE/DROP INDEX CONCURRENTLY`, so they must run outside an
explicit transaction. No DDL was executed in production by this phase.

### Definitive Flyway migrations

The official schema evolution is now:

- `V28__excerpt_rank_latest_score_idx.sql`
- `V29__excerpt_eligible_asset_version_idx.sql`

Each migration has a matching `.sql.conf` with `executeInTransaction=false`.
The stable index names are `excerpt_rank_latest_score_idx` and
`excerpt_eligible_asset_version_idx`. Rollback is intentionally operational,
not a Flyway undo migration:
`drop-feed-score-indexes-definitive.sql`.

### Feed observability

`FeedMetrics` records low-cardinality timers under
`bookrush.feed.stage.duration` with `stage` values:

`history_count`, `catalog_fetch`, `analytics_scores`, `ranking`,
`behavior_publish`, `excerpt_enrichment`, `ledger_persist`, and `total`.

The controller closes `total` in `finally`, including authentication and
remote failures, and records one low-cardinality request outcome: `success`,
`error`, `timeout`, or `empty`.

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
deviation, p50, p95, p99, throughput based on each battery's wall-clock
duration (including concurrency), separate HTTP and functional error rates,
and mean items. It no longer
uses `curl -k`; configure the host trust chain or `CURL_CA_BUNDLE` in the
authorized environment. The existing raw request TSV remains the source of
truth.

Throughput is now `completed_requests / (last_finished_ms -
first_started_ms)`, rather than `1000 / mean_latency`; this prevents a
concurrent battery from being reported as if it were sequential. Functional
failures (`2xx` with fewer than `EXPECTED_MIN_ITEMS`, default `1`) are
reported separately from HTTP errors.

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
after the transaction. The test constructs `JdbcTemplate` and
`DataSourceTransactionManager` from one shared `DataSource`, checks
`pg_backend_pid()` inside the transaction, and verifies the setting after both
commit and rollback.

The IT is opt-in (`RUN_SQL_INTEGRATION=true`) because the regular Jenkins
service test container does not necessarily expose a Docker daemon. During the
first attempt, the analytics POM inherited Testcontainers 1.19.8/docker-java
3.3.6 and failed against the current daemon (client API 1.32; daemon minimum
1.40). The POM now imports the platform Testcontainers BOM at 1.20.2
(docker-java 3.4.0). The explicit IT was then rerun successfully against
PostgreSQL 16: 3 tests, 0 failures, 0 errors, covering Flyway creation, index
validity, safe re-execution, SQL equivalence and JIT transaction isolation.
The test used
`TESTCONTAINERS_RYUK_DISABLED=true` only to avoid a disposable cleanup sidecar;
the container itself was removed by the test process and no production volume
was involved.

Commands:

```bash
docker build --target build -t bookrush/book-analytics-service:feed-optimization-test-build \
  services/book-analytics-service
docker run --rm bookrush/book-analytics-service:feed-optimization-test-build mvn -B test

docker run --rm -e RUN_SQL_INTEGRATION=true \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -v /var/run/docker.sock:/var/run/docker.sock \
  bookrush/book-analytics-service:feed-optimization-test-build \
  mvn -B -Dtest=BookScoresQueryRepositoryIT test

docker build --target build -t bookrush/recommendation-service:feed-optimization-test \
  -f services/recommendation-service/Dockerfile .
docker run --rm bookrush/recommendation-service:feed-optimization-test mvn -B test

bash -n scripts/benchmark-feed-performance.sh scripts/benchmark-score-jit.sh \
  scripts/tests/test-benchmark-score-cleanup.sh
bash scripts/tests/test-benchmark-score-cleanup.sh
```

SQL files are not Bash. Validate them in a disposable database with `psql -f`
and execute the index script only when `SCORE_INDEXES=present` and
`ALLOW_EXPERIMENTAL_DDL=true` are explicitly set. The score benchmark now
accepts `SCORE_QUERY_STRATEGY=CURRENT|OPTIMIZED` and
`SCORE_INDEXES=absent|present`; it records which indexes existed before the
run, never removes definitive or pre-existing experimental indexes, and drops
only experimental indexes created by that execution. The service builds in
this phase passed: analytics 93 tests plus the opt-in SQL integration test and
recommendation 15 tests. Focused client/metrics tests pass 5/5.
The cleanup behavior is covered by `scripts/tests/test-benchmark-score-cleanup.sh`
using a fake Docker/psql boundary, including definitive and pre-existing
experimental indexes.

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

## Controlled run in the non-client environment

The current environment was explicitly authorized for the experiment on
2026-10-19. It contained 518 books, 540,772 excerpts and 415,912 ranks. The
candidate indexes were created with `CONCURRENTLY` only during the `present`
cells and were confirmed absent after each run. Raw sanitized outputs are kept
under the ignored directory
`docs/operations/incidents/evidence/raw/feed-optimization-phase-2/`.

SQL-only matrix, 10 sequential paired rounds per JIT mode plus 5 rounds of 4
parallel sessions (the sequential p50/p95 values are shown):

| Strategy | Indexes | JIT | n sequential | p50 ms | p95 ms | p99 ms |
|---|---|---|---:|---:|---:|---:|
| CURRENT | absent | on | 10 | 953.4 | 2070.3 | 2070.3 |
| CURRENT | absent | off | 10 | 611.1 | 640.8 | 640.8 |
| OPTIMIZED | absent | on | 10 | 933.4 | 994.6 | 994.6 |
| OPTIMIZED | absent | off | 10 | 599.2 | 643.9 | 643.9 |
| CURRENT | present | on | 10 | 513.6 | 538.4 | 538.4 |
| CURRENT | present | off | 10 | 208.6 | 253.0 | 253.0 |
| OPTIMIZED | present | on | 10 | 517.4 | 561.0 | 561.0 |
| OPTIMIZED | present | off | 10 | 210.3 | 236.5 | 236.5 |

The indexes are the dominant improvement in this dataset. The optimized query
does not beat `CURRENT` consistently once the indexes are present, so the
query rewrite is not recommended as a standalone production optimization.
JIT OFF is also materially faster, but it was tested as a transaction-local
setting and is not a global recommendation.

Endpoint matrix through the public gateway used the same protected OIDC test
user and 518-book population. Every completed cell returned HTTP 200 with zero
errors; size 50 returned 24–25 items on average. The two 15-sample cells are
the primary comparison; the 10-sample isolation cells are directional:

| Strategy/JIT | Scenario | n | p50 ms | p95 ms | p99 ms | mean items |
|---|---|---:|---:|---:|---:|---:|
| CURRENT/DEFAULT | sequential size 50 | 15 | 812 | 1408 | 1408 | 24.0 |
| CURRENT/DEFAULT | parallel size 50 | 20 | 1179.5 | 1409 | 1410 | 24.0 |
| CURRENT/DEFAULT | worker paused size 50 | 15 | 771 | 920 | 920 | 24.0 |
| OPTIMIZED/OFF | sequential size 50 | 15 | 572 | 693 | 693 | 24.0 |
| OPTIMIZED/OFF | parallel size 50 | 20 | 770 | 797 | 848 | 24.0 |
| OPTIMIZED/OFF | worker paused size 50 | 15 | 527 | 994 | 994 | 24.1 |
| CURRENT/OFF | sequential size 50 | 10 | 568 | 745 | 745 | 25.0 |
| OPTIMIZED/DEFAULT | sequential size 50 | 10 | 798.5 | 907 | 907 | 25.0 |

The first feed attempt produced 80 HTTP 502 responses because the analytics
container had crashed during startup and recommendation was only in `Created`.
The root cause was `BookScoresQueryRepository` being `final` while Spring's
`@Repository` exception-translation post-processor required a CGLIB proxy. The
class is now proxyable; analytics and recommendation were rebuilt/recreated,
health checks passed, and the feed battery was repeated successfully. The
environment was restored to `CURRENT/DEFAULT`, the analytics worker was left
running, and no production client traffic was present.

These results support **adopting only the new indexes** after a reviewed
staging migration and rollout plan. The definitive migrations are ready for
that rollout, but they do not approve executing the migration in the
production database
or a global JIT change: the query rewrite should remain opt-in, and the JIT
choice requires a separate operational decision after observing connection,
write and workload effects.

## Rollback

1. Set `ANALYTICS_BOOK_SCORES_STRATEGY=CURRENT` and
   `ANALYTICS_BOOK_SCORES_JIT=DEFAULT`.
2. Restart only the analytics service after a reviewed configuration deploy;
   no data migration is required.
3. If the definitive indexes must be rolled back after deployment, execute
   `scripts/experimental/drop-feed-score-indexes-definitive.sql` outside a
   transaction, then redeploy/restart the analytics service only if required.
4. If experimental indexes were created in staging, run
   `scripts/experimental/drop-feed-score-indexes.sql` outside a transaction.
5. Do not run either rollback script against production without the approved
   migration rollback window.
