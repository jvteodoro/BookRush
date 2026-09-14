CREATE TABLE analytics.analysis_job (
  id UUID PRIMARY KEY,
  operation_key VARCHAR(180) NOT NULL UNIQUE,
  status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED')),
  requested_by VARCHAR(160),
  configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
  total_items INTEGER NOT NULL DEFAULT 0 CHECK (total_items >= 0),
  processed_items INTEGER NOT NULL DEFAULT 0 CHECK (processed_items >= 0),
  succeeded_items INTEGER NOT NULL DEFAULT 0 CHECK (succeeded_items >= 0),
  failed_items INTEGER NOT NULL DEFAULT 0 CHECK (failed_items >= 0),
  cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  CHECK (processed_items <= total_items),
  CHECK (succeeded_items + failed_items <= processed_items),
  CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
CREATE INDEX analysis_job_status_idx ON analytics.analysis_job(status, created_at);

CREATE TABLE analytics.analysis_job_item (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES analytics.analysis_job(id) ON DELETE RESTRICT,
  input_asset_version_id UUID NOT NULL REFERENCES catalog.book_asset_version(id) ON DELETE RESTRICT,
  status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  analysis_run_id UUID REFERENCES analytics.analysis_run(id) ON DELETE RESTRICT,
  error_code VARCHAR(80),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  UNIQUE (job_id, input_asset_version_id)
);
CREATE INDEX analysis_job_item_status_idx ON analytics.analysis_job_item(job_id, status);
