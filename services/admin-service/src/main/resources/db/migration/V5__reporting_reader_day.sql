CREATE SCHEMA IF NOT EXISTS reporting;

CREATE TABLE IF NOT EXISTS reporting.reader_day (
  day DATE NOT NULL,
  population VARCHAR(32) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  active_users BIGINT,
  newly_active_users BIGINT,
  sessions BIGINT,
  active_seconds BIGINT,
  book_opens BIGINT,
  meaningful_reads BIGINT,
  completions BIGINT,
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (day, population, platform)
);

CREATE TABLE IF NOT EXISTS reporting.projection_health (
  projection_name VARCHAR(80) PRIMARY KEY,
  last_successful_refresh TIMESTAMPTZ,
  source_watermark TIMESTAMPTZ,
  record_count BIGINT NOT NULL DEFAULT 0,
  definition_version VARCHAR(80) NOT NULL,
  refresh_status VARCHAR(24) NOT NULL,
  last_error_code VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS reporting_reader_day_day_idx
  ON reporting.reader_day(day DESC, population, platform);
