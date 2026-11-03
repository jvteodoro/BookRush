CREATE TABLE analytics.analysis_phase_log (
  id UUID PRIMARY KEY,
  phase_timing_id UUID NOT NULL REFERENCES analytics.analysis_phase_timing(id) ON DELETE CASCADE,
  job_id UUID NOT NULL REFERENCES analytics.analysis_job(id) ON DELETE RESTRICT,
  job_item_id UUID NOT NULL REFERENCES analytics.analysis_job_item(id) ON DELETE RESTRICT,
  attempt_count INTEGER NOT NULL CHECK (attempt_count >= 1),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  level VARCHAR(16) NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
  message TEXT NOT NULL,
  progress_completed INTEGER,
  progress_total INTEGER,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CHECK (progress_completed IS NULL OR progress_completed >= 0),
  CHECK (progress_total IS NULL OR progress_total >= 0)
);

CREATE INDEX analysis_phase_log_timing_idx
  ON analytics.analysis_phase_log(phase_timing_id, occurred_at, id);
CREATE INDEX analysis_phase_log_item_idx
  ON analytics.analysis_phase_log(job_item_id, attempt_count, occurred_at);
