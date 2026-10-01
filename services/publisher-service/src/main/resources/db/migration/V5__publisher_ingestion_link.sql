ALTER TABLE publisher.submission
  ADD COLUMN IF NOT EXISTS source_code TEXT,
  ADD COLUMN IF NOT EXISTS source_external_id TEXT,
  ADD COLUMN IF NOT EXISTS ingestion_job_id UUID,
  ADD COLUMN IF NOT EXISTS linked_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS submission_source_external_idx
  ON publisher.submission(source_code, source_external_id)
  WHERE source_code IS NOT NULL AND source_external_id IS NOT NULL;
