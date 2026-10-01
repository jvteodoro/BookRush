CREATE TABLE IF NOT EXISTS behavior.events (
 id UUID PRIMARY KEY, event_key VARCHAR(200) NOT NULL UNIQUE, event_type VARCHAR(80) NOT NULL,
 identity_issuer TEXT, identity_subject TEXT, book_id UUID, payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_behavior_events_subject_time ON behavior.events(identity_subject, occurred_at);
