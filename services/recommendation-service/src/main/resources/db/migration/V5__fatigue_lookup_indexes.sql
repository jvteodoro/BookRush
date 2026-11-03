CREATE INDEX IF NOT EXISTS recommendation_request_subject_id_idx
  ON recommendation.request(subject_key, id);

CREATE INDEX IF NOT EXISTS recommendation_impression_request_book_shown_idx
  ON recommendation.impression(request_id, book_id, shown_at DESC)
  WHERE viewable = true;
