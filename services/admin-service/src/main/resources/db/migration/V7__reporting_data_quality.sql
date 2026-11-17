CREATE TABLE IF NOT EXISTS reporting.attribution_daily (
  day DATE NOT NULL,
  attribution_status VARCHAR(32) NOT NULL,
  outcome_count BIGINT NOT NULL CHECK (outcome_count >= 0),
  unique_events BIGINT NOT NULL CHECK (unique_events >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ,
  PRIMARY KEY (day, attribution_status)
);

CREATE TABLE IF NOT EXISTS reporting.reconciliation_daily (
  day DATE PRIMARY KEY,
  ledger_impression_count BIGINT NOT NULL CHECK (ledger_impression_count >= 0),
  ledger_viewable_count BIGINT NOT NULL CHECK (ledger_viewable_count >= 0),
  behavior_impression_event_count BIGINT NOT NULL CHECK (behavior_impression_event_count >= 0),
  behavior_unique_impression_count BIGINT NOT NULL CHECK (behavior_unique_impression_count >= 0),
  mismatched_impression_count BIGINT NOT NULL CHECK (mismatched_impression_count >= 0),
  duplicate_event_count BIGINT NOT NULL CHECK (duplicate_event_count >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_attribution_daily_day_idx
  ON reporting.attribution_daily(day DESC, attribution_status);

CREATE INDEX IF NOT EXISTS reporting_reconciliation_daily_day_idx
  ON reporting.reconciliation_daily(day DESC);
