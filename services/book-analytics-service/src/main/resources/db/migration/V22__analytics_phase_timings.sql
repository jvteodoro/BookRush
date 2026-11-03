CREATE TABLE analytics.analysis_phase_timing (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES analytics.analysis_job(id) ON DELETE RESTRICT,
  job_item_id UUID NOT NULL REFERENCES analytics.analysis_job_item(id) ON DELETE RESTRICT,
  analysis_run_id UUID REFERENCES analytics.analysis_run(id) ON DELETE RESTRICT,
  attempt_count INTEGER NOT NULL CHECK (attempt_count >= 1),
  phase VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMPTZ,
  duration_ms BIGINT,
  error_code VARCHAR(120),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  CHECK (finished_at IS NULL OR finished_at >= started_at),
  CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX analysis_phase_timing_job_idx
  ON analytics.analysis_phase_timing(job_id, phase, started_at);
CREATE INDEX analysis_phase_timing_distribution_idx
  ON analytics.analysis_phase_timing(phase, status, started_at);
