ALTER TABLE recommendation.request
  ADD COLUMN IF NOT EXISTS strategy VARCHAR(24) NOT NULL DEFAULT 'PERSONALIZED'
    CHECK (strategy IN ('PERSONALIZED', 'RANDOM', 'TRENDING', 'FEED')),
  ADD COLUMN IF NOT EXISTS random_seed BIGINT,
  ADD COLUMN IF NOT EXISTS candidate_set_hash VARCHAR(64)
    CHECK (candidate_set_hash IS NULL OR candidate_set_hash ~ '^[0-9a-f]{64}$'),
  ADD COLUMN IF NOT EXISTS context JSONB NOT NULL DEFAULT '{}'::jsonb
    CHECK (jsonb_typeof(context) = 'object');

CREATE TABLE IF NOT EXISTS recommendation.item (
  id UUID PRIMARY KEY,
  request_id UUID NOT NULL REFERENCES recommendation.request(id) ON DELETE RESTRICT,
  book_id UUID NOT NULL,
  excerpt_id UUID,
  rank INTEGER NOT NULL CHECK (rank > 0),
  score NUMERIC(12,6),
  reason JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(reason) = 'object'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (request_id, rank),
  UNIQUE (request_id, book_id)
);

ALTER TABLE recommendation.impression
  ADD COLUMN IF NOT EXISTS item_id UUID REFERENCES recommendation.item(id) ON DELETE RESTRICT;

-- Legacy impressions retain their old request/book/rank fields. A row in item
-- makes the new relation available without losing the historical ledger.
INSERT INTO recommendation.item (id, request_id, book_id, rank, reason)
SELECT id, request_id, book_id, rank, jsonb_build_object('source', 'legacy-impression')
FROM recommendation.impression
ON CONFLICT (id) DO NOTHING;

UPDATE recommendation.impression
SET item_id = id
WHERE item_id IS NULL;

CREATE INDEX IF NOT EXISTS recommendation_item_book_idx
  ON recommendation.item(book_id, created_at DESC);
CREATE INDEX IF NOT EXISTS recommendation_request_subject_strategy_idx
  ON recommendation.request(subject_key, strategy, created_at DESC);
