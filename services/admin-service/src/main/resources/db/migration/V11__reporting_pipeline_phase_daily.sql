CREATE TABLE IF NOT EXISTS reporting.pipeline_phase_daily (
    day DATE NOT NULL,
    phase VARCHAR(64) NOT NULL,
    phase_status VARCHAR(16) NOT NULL,
    error_code VARCHAR(120) NOT NULL,
    attempts BIGINT NOT NULL,
    completed_count BIGINT NOT NULL,
    failed_count BIGINT NOT NULL,
    stuck_count BIGINT NOT NULL,
    p50_duration_ms DOUBLE PRECISION,
    p95_duration_ms DOUBLE PRECISION,
    p99_duration_ms DOUBLE PRECISION,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, phase, phase_status, error_code)
);

CREATE INDEX IF NOT EXISTS reporting_pipeline_phase_daily_idx
  ON reporting.pipeline_phase_daily(day DESC, phase, phase_status);
