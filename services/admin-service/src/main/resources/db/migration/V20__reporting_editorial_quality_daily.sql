CREATE TABLE IF NOT EXISTS reporting.editorial_quality_daily (
    day DATE PRIMARY KEY,
    campaigns BIGINT NOT NULL CHECK (campaigns >= 0),
    sampled_items BIGINT NOT NULL CHECK (sampled_items >= 0),
    pending_items BIGINT NOT NULL CHECK (pending_items >= 0),
    in_progress_items BIGINT NOT NULL CHECK (in_progress_items >= 0),
    completed_items BIGINT NOT NULL CHECK (completed_items >= 0),
    annotations BIGINT NOT NULL CHECK (annotations >= 0),
    submitted_annotations BIGINT NOT NULL CHECK (submitted_annotations >= 0),
    unique_annotators BIGINT NOT NULL CHECK (unique_annotators >= 0),
    disputed_items BIGINT NOT NULL CHECK (disputed_items >= 0),
    adjudications BIGINT NOT NULL CHECK (adjudications >= 0),
    p50_elapsed_ms DOUBLE PRECISION,
    p95_elapsed_ms DOUBLE PRECISION,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_editorial_quality_daily_day_idx
  ON reporting.editorial_quality_daily(day DESC);
