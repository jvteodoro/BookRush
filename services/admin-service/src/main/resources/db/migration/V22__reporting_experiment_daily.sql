CREATE TABLE IF NOT EXISTS reporting.experiment_daily (
    day DATE NOT NULL,
    experiment_id VARCHAR(96) NOT NULL,
    assignment_version VARCHAR(96) NOT NULL,
    variant VARCHAR(32) NOT NULL,
    assigned_subjects BIGINT NOT NULL,
    request_count BIGINT NOT NULL,
    impression_count BIGINT NOT NULL,
    viewable_count BIGINT NOT NULL,
    attributed_open_count BIGINT NOT NULL,
    attributed_read_count BIGINT NOT NULL,
    attributed_complete_count BIGINT NOT NULL,
    unique_subjects BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, experiment_id, assignment_version, variant)
);

CREATE INDEX IF NOT EXISTS reporting_experiment_daily_idx
  ON reporting.experiment_daily(experiment_id, day DESC, variant);
