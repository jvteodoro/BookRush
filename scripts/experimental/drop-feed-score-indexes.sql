-- Experimental rollback. Run outside an explicit transaction because the
-- indexes were created with CREATE INDEX CONCURRENTLY.
DROP INDEX CONCURRENTLY IF EXISTS analytics.feed_score_rank_latest_experiment_idx;
DROP INDEX CONCURRENTLY IF EXISTS analytics.feed_score_excerpt_eligible_experiment_idx;
