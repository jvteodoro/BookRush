ALTER TABLE behavior.outcome_attribution
  ADD COLUMN IF NOT EXISTS exposure_event_id UUID,
  ADD COLUMN IF NOT EXISTS attributed_exposure_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS behavior_outcome_exposure_event_idx
  ON behavior.outcome_attribution(exposure_event_id, occurred_at DESC)
  WHERE exposure_event_id IS NOT NULL;
