CREATE TABLE IF NOT EXISTS reporting.recommendation_position_funnel_daily (
  day DATE NOT NULL,
  model_version VARCHAR(160) NOT NULL,
  strategy VARCHAR(32) NOT NULL,
  rank_bucket VARCHAR(16) NOT NULL,
  population VARCHAR(32) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  impression_count BIGINT NOT NULL DEFAULT 0 CHECK (impression_count >= 0),
  viewable_count BIGINT NOT NULL DEFAULT 0 CHECK (viewable_count >= 0),
  eligible_viewable_count BIGINT NOT NULL DEFAULT 0 CHECK (eligible_viewable_count >= 0),
  attributed_open_count BIGINT NOT NULL DEFAULT 0 CHECK (attributed_open_count >= 0),
  mature_attributed_open_count BIGINT NOT NULL DEFAULT 0 CHECK (mature_attributed_open_count >= 0),
  calculation_version VARCHAR(120) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (day, model_version, strategy, rank_bucket, population, platform)
);

CREATE INDEX IF NOT EXISTS reporting_position_funnel_day_idx
  ON reporting.recommendation_position_funnel_daily(day DESC, strategy, rank_bucket);
