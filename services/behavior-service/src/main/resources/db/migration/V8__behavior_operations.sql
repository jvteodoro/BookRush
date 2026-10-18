ALTER TABLE behavior.outbox
  ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS behavior_events_type_time_idx
  ON behavior.events(event_type, occurred_at DESC);
CREATE INDEX IF NOT EXISTS behavior_events_book_time_idx
  ON behavior.events(book_id, occurred_at DESC)
  WHERE book_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS behavior.outcome_attribution (
  id UUID PRIMARY KEY,
  subject_key TEXT NOT NULL,
  book_id UUID NOT NULL,
  outcome_type VARCHAR(40) NOT NULL,
  source_event_id UUID NOT NULL REFERENCES behavior.events(id) ON DELETE RESTRICT,
  occurred_at TIMESTAMPTZ NOT NULL,
  UNIQUE (source_event_id, outcome_type)
);

CREATE INDEX IF NOT EXISTS behavior_outcome_subject_book_idx
  ON behavior.outcome_attribution(subject_key, book_id, occurred_at DESC);
