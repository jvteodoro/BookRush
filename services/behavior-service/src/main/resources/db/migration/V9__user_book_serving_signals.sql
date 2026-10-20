CREATE TABLE IF NOT EXISTS behavior.user_book_signal (
  identity_subject TEXT NOT NULL,
  book_id UUID NOT NULL,
  impression_count BIGINT NOT NULL DEFAULT 0 CHECK (impression_count >= 0),
  viewable_impression_count BIGINT NOT NULL DEFAULT 0 CHECK (viewable_impression_count >= 0),
  open_count BIGINT NOT NULL DEFAULT 0 CHECK (open_count >= 0),
  progress_event_count BIGINT NOT NULL DEFAULT 0 CHECK (progress_event_count >= 0),
  completion_count BIGINT NOT NULL DEFAULT 0 CHECK (completion_count >= 0),
  active_seconds BIGINT NOT NULL DEFAULT 0 CHECK (active_seconds >= 0),
  max_progress NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (max_progress >= 0 AND max_progress <= 100),
  liked BOOLEAN NOT NULL DEFAULT FALSE,
  saved BOOLEAN NOT NULL DEFAULT FALSE,
  first_event_at TIMESTAMPTZ NOT NULL,
  last_event_at TIMESTAMPTZ NOT NULL,
  last_opened_at TIMESTAMPTZ,
  last_progress_at TIMESTAMPTZ,
  last_impression_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (identity_subject, book_id)
);

CREATE INDEX IF NOT EXISTS behavior_user_book_signal_subject_time_idx
  ON behavior.user_book_signal(identity_subject, last_event_at DESC);
CREATE INDEX IF NOT EXISTS behavior_user_book_signal_book_time_idx
  ON behavior.user_book_signal(book_id, last_event_at DESC);
