ALTER TABLE behavior.outcome_attribution
  ADD COLUMN IF NOT EXISTS recommendation_item_id UUID,
  ADD COLUMN IF NOT EXISTS impression_id UUID;

CREATE INDEX IF NOT EXISTS behavior_outcome_subject_item_time_idx
  ON behavior.outcome_attribution(subject_key, recommendation_item_id, occurred_at DESC)
  WHERE recommendation_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS behavior_outcome_subject_impression_time_idx
  ON behavior.outcome_attribution(subject_key, impression_id, occurred_at DESC)
  WHERE impression_id IS NOT NULL;
