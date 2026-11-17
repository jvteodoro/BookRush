CREATE TABLE IF NOT EXISTS reporting.rights_status_daily (
    day DATE NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    right_type VARCHAR(24) NOT NULL,
    territory VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    item_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, source_type, right_type, territory, status)
);

CREATE INDEX IF NOT EXISTS reporting_rights_status_day_idx
  ON reporting.rights_status_daily(day DESC, source_type, right_type, status);
