-- Operational rollback for V28/V29. Run outside an explicit transaction.
-- This file is intentionally not a Flyway migration.
DROP INDEX CONCURRENTLY IF EXISTS analytics.excerpt_rank_latest_score_idx;
DROP INDEX CONCURRENTLY IF EXISTS analytics.excerpt_eligible_asset_version_idx;
