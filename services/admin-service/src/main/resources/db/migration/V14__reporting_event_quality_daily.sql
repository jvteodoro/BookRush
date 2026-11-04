CREATE TABLE IF NOT EXISTS reporting.event_quality_daily (
    day DATE PRIMARY KEY,
    accepted_events BIGINT NOT NULL,
    rejected_events BIGINT NOT NULL,
    duplicate_rejections BIGINT NOT NULL,
    schema_rejections BIGINT NOT NULL,
    payload_rejections BIGINT NOT NULL,
    outbox_pending BIGINT NOT NULL,
    outbox_published BIGINT NOT NULL,
    outbox_failed_attempts BIGINT NOT NULL,
    outbox_oldest_pending_age_seconds BIGINT,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_event_quality_daily_day_idx
  ON reporting.event_quality_daily(day DESC);
