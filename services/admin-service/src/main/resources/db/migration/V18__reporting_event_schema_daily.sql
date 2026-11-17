CREATE TABLE IF NOT EXISTS reporting.event_schema_daily (
    day DATE NOT NULL,
    record_kind VARCHAR(12) NOT NULL CHECK (record_kind IN ('ACCEPTED', 'REJECTED')),
    event_type VARCHAR(80) NOT NULL,
    schema_version VARCHAR(80) NOT NULL,
    client_version VARCHAR(80) NOT NULL,
    reject_reason VARCHAR(80),
    record_count BIGINT NOT NULL,
    unknown_event_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, record_kind, event_type, schema_version, client_version, reject_reason)
);

CREATE INDEX IF NOT EXISTS reporting_event_schema_day_idx
  ON reporting.event_schema_daily(day DESC, record_kind, event_type);
