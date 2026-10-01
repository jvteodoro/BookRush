CREATE TABLE IF NOT EXISTS behavior.outbox (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  event_type TEXT NOT NULL,
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS behavior_outbox_pending_idx ON behavior.outbox(created_at)
  WHERE published_at IS NULL;
