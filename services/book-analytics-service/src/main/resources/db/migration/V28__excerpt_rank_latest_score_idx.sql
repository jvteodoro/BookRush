-- flyway:executeInTransaction=false
-- Latest non-null rank lookup for the score endpoint.
CREATE INDEX CONCURRENTLY IF NOT EXISTS excerpt_rank_latest_score_idx
    ON analytics.excerpt_rank (excerpt_id, created_at DESC)
    WHERE final_score IS NOT NULL;
