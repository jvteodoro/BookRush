ALTER TABLE recommendation.request DROP CONSTRAINT IF EXISTS request_strategy_check;
ALTER TABLE recommendation.request
  ADD CONSTRAINT recommendation_request_strategy_check
  CHECK (strategy IN ('PERSONALIZED', 'RANDOM', 'TRENDING', 'FEED', 'COLD_START', 'DIVERSE', 'LUCKY'));
