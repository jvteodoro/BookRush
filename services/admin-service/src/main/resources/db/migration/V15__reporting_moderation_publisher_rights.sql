CREATE TABLE IF NOT EXISTS reporting.moderation_daily (
    day DATE PRIMARY KEY,
    open_count BIGINT NOT NULL,
    in_review_count BIGINT NOT NULL,
    resolved_count BIGINT NOT NULL,
    dismissed_count BIGINT NOT NULL,
    p50_resolution_seconds DOUBLE PRECISION,
    p95_resolution_seconds DOUBLE PRECISION,
    oldest_open_age_seconds BIGINT,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS reporting.publisher_workflow_daily (
    day DATE PRIMARY KEY,
    submissions BIGINT NOT NULL,
    approved_reviews BIGINT NOT NULL,
    rejected_reviews BIGINT NOT NULL,
    changes_requested BIGINT NOT NULL,
    published_works BIGINT NOT NULL,
    active_published_works BIGINT NOT NULL,
    rights_attestations BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS reporting.rights_daily (
    day DATE NOT NULL,
    action VARCHAR(24) NOT NULL,
    distribution_status VARCHAR(24) NOT NULL,
    decision_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, action, distribution_status)
);

CREATE INDEX IF NOT EXISTS reporting_moderation_daily_day_idx
  ON reporting.moderation_daily(day DESC);
CREATE INDEX IF NOT EXISTS reporting_publisher_workflow_daily_day_idx
  ON reporting.publisher_workflow_daily(day DESC);
CREATE INDEX IF NOT EXISTS reporting_rights_daily_day_idx
  ON reporting.rights_daily(day DESC, action, distribution_status);
