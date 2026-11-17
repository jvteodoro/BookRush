CREATE TABLE reporting.import_quality_daily (
    day DATE NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    item_status VARCHAR(24) NOT NULL,
    error_bucket VARCHAR(24) NOT NULL,
    rights_outcome VARCHAR(32) NOT NULL,
    asset_outcome VARCHAR(32) NOT NULL,
    item_count BIGINT NOT NULL,
    retry_attempt_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, source_code, item_status, error_bucket, rights_outcome, asset_outcome)
);

CREATE INDEX reporting_import_quality_day_idx
  ON reporting.import_quality_daily(day DESC, source_code, item_status);
