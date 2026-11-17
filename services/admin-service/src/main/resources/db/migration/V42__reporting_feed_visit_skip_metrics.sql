ALTER TABLE reporting.feed_daily
  ADD COLUMN skipped_visits BIGINT NOT NULL DEFAULT 0
    CHECK (skipped_visits >= 0);
