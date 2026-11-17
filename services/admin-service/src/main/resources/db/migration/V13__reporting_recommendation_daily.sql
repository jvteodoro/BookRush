CREATE TABLE IF NOT EXISTS reporting.recommendation_daily (
    day DATE NOT NULL,
    model_version VARCHAR(160) NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    feed_path VARCHAR(80) NOT NULL,
    population VARCHAR(48) NOT NULL,
    platform VARCHAR(48) NOT NULL,
    request_count BIGINT NOT NULL,
    item_count BIGINT NOT NULL,
    impression_count BIGINT NOT NULL,
    viewable_count BIGINT NOT NULL,
    unique_books BIGINT NOT NULL,
    p50_rank DOUBLE PRECISION,
    p90_rank DOUBLE PRECISION,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, model_version, strategy, feed_path, population, platform)
);

CREATE INDEX IF NOT EXISTS reporting_recommendation_daily_day_idx
  ON reporting.recommendation_daily(day DESC, strategy, model_version);
