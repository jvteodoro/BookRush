# Feed score SQL experiment

These scripts are deliberately outside Flyway. They are for a disposable or
staging PostgreSQL copy only:

```bash
psql "$EXPERIMENT_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -f scripts/experimental/feed-score-indexes.sql

psql "$EXPERIMENT_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -f scripts/experimental/drop-feed-score-indexes.sql
```

The commands must not be run against production. `CREATE/DROP INDEX
CONCURRENTLY` cannot run inside a transaction; the scripts therefore must be
invoked as standalone `psql` scripts. Capture
`EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)` before and after the
indexes, together with index sizes and write-cost observations.
