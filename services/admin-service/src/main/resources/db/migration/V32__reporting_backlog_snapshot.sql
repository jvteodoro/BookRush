CREATE TABLE reporting.backlog_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    queue_name VARCHAR(80) NOT NULL,
    pending_count BIGINT NOT NULL,
    oldest_pending_age_seconds BIGINT,
    failed_count BIGINT NOT NULL,
    stuck_count BIGINT NOT NULL,
    last_successful_at TIMESTAMPTZ,
    source_watermark TIMESTAMPTZ,
    record_count BIGINT NOT NULL,
    refresh_status VARCHAR(24) NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (collected_at, source_type, queue_name)
);

CREATE INDEX reporting_backlog_time_idx
  ON reporting.backlog_snapshot(collected_at DESC, source_type, queue_name);
