-- Private access state is independent of the limited recent-history response.
CREATE TABLE reader_state.seen_book (
  subject_key TEXT NOT NULL,
  book_id UUID NOT NULL,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (subject_key, book_id),
  CHECK (last_seen_at >= first_seen_at)
);
-- Existing positive reading progress is evidence of loaded content. A legacy
-- opening attempt alone is not: older clients recorded it before loading.
INSERT INTO reader_state.seen_book(subject_key, book_id, first_seen_at, last_seen_at)
SELECT subject_key, book_id, updated_at, updated_at
FROM reader_state.progress WHERE percent > 0 OR position_codepoint > 0;
