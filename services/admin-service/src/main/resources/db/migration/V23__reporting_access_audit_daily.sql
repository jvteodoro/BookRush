CREATE TABLE IF NOT EXISTS reporting.access_audit_daily (
    day DATE NOT NULL,
    action VARCHAR(120) NOT NULL,
    event_count BIGINT NOT NULL,
    unique_actors BIGINT NOT NULL,
    unique_targets BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, action)
);

CREATE INDEX IF NOT EXISTS reporting_access_audit_day_idx
  ON reporting.access_audit_daily(day DESC, action);
