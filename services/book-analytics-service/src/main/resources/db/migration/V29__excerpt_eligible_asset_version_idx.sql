-- flyway:executeInTransaction=false
-- Restrict score scans to eligible excerpts of the requested asset version.
CREATE INDEX CONCURRENTLY IF NOT EXISTS excerpt_eligible_asset_version_idx
    ON analytics.excerpt (source_asset_version_id, id)
    WHERE body_eligible = TRUE AND exclusion_reason IS NULL;
