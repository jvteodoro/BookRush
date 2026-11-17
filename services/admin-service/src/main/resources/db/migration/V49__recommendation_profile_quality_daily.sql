CREATE TABLE reporting.recommendation_profile_quality_daily (
  day DATE NOT NULL,
  model_version VARCHAR(160) NOT NULL,
  strategy VARCHAR(32) NOT NULL,
  feed_path VARCHAR(80) NOT NULL,
  population VARCHAR(48) NOT NULL,
  platform VARCHAR(48) NOT NULL,
  confidence_bucket VARCHAR(24) NOT NULL,
  profile_size_bucket VARCHAR(24) NOT NULL,
  fatigue_coverage_bucket VARCHAR(24) NOT NULL,
  cold_start BOOLEAN NOT NULL,
  request_count BIGINT NOT NULL CHECK (request_count >= 0),
  calculation_version VARCHAR(120) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (day, model_version, strategy, feed_path, population, platform,
    confidence_bucket, profile_size_bucket, fatigue_coverage_bucket, cold_start)
);

CREATE INDEX reporting_recommendation_profile_quality_daily_day_idx
  ON reporting.recommendation_profile_quality_daily(day DESC, strategy, model_version);
