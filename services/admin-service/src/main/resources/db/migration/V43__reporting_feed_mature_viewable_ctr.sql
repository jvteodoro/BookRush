ALTER TABLE reporting.feed_daily
  ADD COLUMN eligible_viewable_count BIGINT NOT NULL DEFAULT 0
    CHECK (eligible_viewable_count >= 0),
  ADD COLUMN mature_attributed_open_count BIGINT NOT NULL DEFAULT 0
    CHECK (mature_attributed_open_count >= 0);
