ALTER TABLE behavior.events
  ADD COLUMN IF NOT EXISTS identity_population VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_behavior_events_population_time
  ON behavior.events(identity_population, occurred_at);
