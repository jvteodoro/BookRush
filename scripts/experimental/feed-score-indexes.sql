-- Experimental only. Run against a disposable/staging PostgreSQL copy.
-- These statements are intentionally not a Flyway migration and must not be
-- applied to production without a reviewed migration and rollback plan.

CREATE INDEX CONCURRENTLY IF NOT EXISTS feed_score_rank_latest_experiment_idx
    ON analytics.excerpt_rank (excerpt_id, created_at DESC)
    WHERE final_score IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS feed_score_excerpt_eligible_experiment_idx
    ON analytics.excerpt (source_asset_version_id, id)
    WHERE body_eligible = TRUE AND exclusion_reason IS NULL;

ANALYZE analytics.excerpt;
ANALYZE analytics.excerpt_rank;
