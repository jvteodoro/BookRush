ALTER TABLE reporting.recommendation_daily
  ADD COLUMN repeat_viewable_count BIGINT NOT NULL DEFAULT 0
    CHECK (repeat_viewable_count >= 0),
  ADD COLUMN viewable_unique_books BIGINT NOT NULL DEFAULT 0
    CHECK (viewable_unique_books >= 0);
