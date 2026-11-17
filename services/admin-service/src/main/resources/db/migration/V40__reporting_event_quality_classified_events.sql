ALTER TABLE reporting.event_quality_daily
  ADD COLUMN classified_events BIGINT NOT NULL DEFAULT 0;
