CREATE TABLE IF NOT EXISTS behavior.daily_user_book (
  day DATE NOT NULL,
  identity_subject TEXT NOT NULL,
  book_id UUID NOT NULL,
  impressions BIGINT NOT NULL DEFAULT 0,
  opens BIGINT NOT NULL DEFAULT 0,
  likes BIGINT NOT NULL DEFAULT 0,
  reads BIGINT NOT NULL DEFAULT 0,
  active_seconds BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(day, identity_subject, book_id)
);
CREATE INDEX IF NOT EXISTS idx_daily_user_book_subject_day ON behavior.daily_user_book(identity_subject, day DESC);
