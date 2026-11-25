-- New cohort-safe funnel fields remain NULL for historical rows until recomputed.
ALTER TABLE reporting.feed_daily
  ADD COLUMN IF NOT EXISTS mature_served_count BIGINT,
  ADD COLUMN IF NOT EXISTS mature_attributed_significant_read_count BIGINT,
  ADD COLUMN IF NOT EXISTS mature_attributed_complete_count BIGINT;
