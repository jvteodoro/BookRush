ALTER TABLE reporting.recommendation_daily
  ADD COLUMN candidate_count BIGINT CHECK (candidate_count >= 0),
  ADD COLUMN scored_candidate_count BIGINT CHECK (scored_candidate_count >= 0),
  ADD COLUMN selected_count BIGINT CHECK (selected_count >= 0);
