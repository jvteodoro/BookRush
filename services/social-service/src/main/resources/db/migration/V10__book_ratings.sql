CREATE TABLE social.book_rating (
  subject_key TEXT NOT NULL,
  book_id UUID NOT NULL,
  stars SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (subject_key, book_id)
);
CREATE INDEX book_rating_book_idx ON social.book_rating(book_id) INCLUDE (stars);
