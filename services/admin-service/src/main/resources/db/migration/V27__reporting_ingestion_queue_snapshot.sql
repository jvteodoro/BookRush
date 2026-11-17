CREATE TABLE reporting.ingestion_queue_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    queue_status VARCHAR(24) NOT NULL,
    task_count BIGINT NOT NULL,
    oldest_task_age_seconds BIGINT,
    expired_lease_count BIGINT NOT NULL DEFAULT 0,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (collected_at, queue_status)
);

CREATE INDEX reporting_ingestion_queue_time_idx
  ON reporting.ingestion_queue_snapshot(collected_at DESC, queue_status);
