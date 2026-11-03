ALTER TABLE analytics.analysis_job_item
  ADD COLUMN progress_stage VARCHAR(64),
  ADD COLUMN progress_completed INTEGER NOT NULL DEFAULT 0 CHECK (progress_completed >= 0),
  ADD COLUMN progress_total INTEGER NOT NULL DEFAULT 0 CHECK (progress_total >= 0),
  ADD COLUMN progress_message TEXT,
  ADD COLUMN progress_updated_at TIMESTAMPTZ;

UPDATE analytics.analysis_job_item
   SET progress_stage = CASE WHEN status = 'COMPLETED' THEN 'COMPLETED' ELSE status END,
       progress_completed = CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END,
       progress_total = CASE WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN 1 ELSE 0 END,
       progress_updated_at = COALESCE(finished_at, started_at, created_at)
 WHERE progress_updated_at IS NULL;

CREATE INDEX analysis_job_item_progress_idx
  ON analytics.analysis_job_item(job_id, progress_updated_at);
