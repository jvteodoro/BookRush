CREATE TABLE IF NOT EXISTS reporting.recommendation_quality_daily (
    day DATE NOT NULL,
    model_version VARCHAR(160) NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    outcome_type VARCHAR(40) NOT NULL,
    attributed_count BIGINT NOT NULL,
    unique_subjects BIGINT NOT NULL,
    unique_books BIGINT NOT NULL,
    p50_rank DOUBLE PRECISION,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, model_version, strategy, outcome_type)
);

CREATE INDEX IF NOT EXISTS reporting_recommendation_quality_day_idx
  ON reporting.recommendation_quality_daily(day DESC, strategy, outcome_type);
