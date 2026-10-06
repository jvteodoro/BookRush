ALTER TABLE analytics.excerpt
  ADD COLUMN IF NOT EXISTS body_eligible BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS exclusion_reason VARCHAR(64);

CREATE INDEX IF NOT EXISTS excerpt_body_eligible_idx
  ON analytics.excerpt(source_asset_version_id, body_eligible, start_codepoint);
