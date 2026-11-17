CREATE INDEX IF NOT EXISTS recommendation_impression_viewable_book_time_idx
  ON recommendation.impression(
    book_id,
    (COALESCE(viewable_at, shown_at)),
    id
  ) INCLUDE (request_id)
  WHERE viewable = true;
