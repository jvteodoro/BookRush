CREATE TABLE analytics.behavior_event (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(120) NOT NULL,
  payload JSONB NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX behavior_event_type_time_idx
  ON analytics.behavior_event(event_type, occurred_at DESC);
