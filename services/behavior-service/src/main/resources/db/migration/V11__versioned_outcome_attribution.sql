ALTER TABLE behavior.outcome_attribution
  ADD COLUMN IF NOT EXISTS attribution_status VARCHAR(32) NOT NULL DEFAULT 'UNTRUSTED',
  ADD COLUMN IF NOT EXISTS attribution_rule_version VARCHAR(64) NOT NULL DEFAULT 'attribution-v1',
  ADD COLUMN IF NOT EXISTS attribution_window_seconds INTEGER NOT NULL DEFAULT 604800;

CREATE INDEX IF NOT EXISTS behavior_outcome_attribution_status_idx
  ON behavior.outcome_attribution(attribution_status, occurred_at DESC);
