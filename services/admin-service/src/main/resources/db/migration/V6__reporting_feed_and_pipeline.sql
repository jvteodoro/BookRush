CREATE TABLE IF NOT EXISTS reporting.feed_daily (
  day DATE NOT NULL,
  model_version VARCHAR(120) NOT NULL,
  strategy VARCHAR(32) NOT NULL,
  feed_path VARCHAR(64) NOT NULL,
  population VARCHAR(32) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  served_count BIGINT NOT NULL DEFAULT 0 CHECK (served_count >= 0),
  viewable_count BIGINT NOT NULL DEFAULT 0 CHECK (viewable_count >= 0),
  attributed_open_count BIGINT NOT NULL DEFAULT 0 CHECK (attributed_open_count >= 0),
  attributed_read_10_count BIGINT NOT NULL DEFAULT 0 CHECK (attributed_read_10_count >= 0),
  attributed_read_50_count BIGINT NOT NULL DEFAULT 0 CHECK (attributed_read_50_count >= 0),
  attributed_complete_count BIGINT NOT NULL DEFAULT 0 CHECK (attributed_complete_count >= 0),
  dwell_observations BIGINT NOT NULL DEFAULT 0 CHECK (dwell_observations >= 0),
  dwell_seconds BIGINT NOT NULL DEFAULT 0 CHECK (dwell_seconds >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (day, model_version, strategy, feed_path, population, platform)
);

CREATE INDEX IF NOT EXISTS reporting_feed_daily_day_idx
  ON reporting.feed_daily(day DESC, strategy, model_version);

ALTER TABLE reporting.feed_daily
  ALTER COLUMN dwell_observations DROP NOT NULL,
  ALTER COLUMN dwell_seconds DROP NOT NULL;

CREATE TABLE IF NOT EXISTS reporting.pipeline_snapshot (
  collected_at TIMESTAMPTZ NOT NULL,
  service VARCHAR(64) NOT NULL,
  job_status VARCHAR(32) NOT NULL,
  phase VARCHAR(64) NOT NULL,
  status_count BIGINT NOT NULL CHECK (status_count >= 0),
  oldest_pending_age_seconds BIGINT,
  failure_count BIGINT NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
  throughput BIGINT NOT NULL DEFAULT 0 CHECK (throughput >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (collected_at, service, job_status, phase)
);

CREATE INDEX IF NOT EXISTS reporting_pipeline_snapshot_time_idx
  ON reporting.pipeline_snapshot(collected_at DESC, service, phase);
