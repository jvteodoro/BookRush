CREATE TABLE IF NOT EXISTS publisher.staged_upload (
  id UUID PRIMARY KEY,
  submission_id UUID NOT NULL REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  subject_key TEXT NOT NULL,
  object_key TEXT NOT NULL UNIQUE,
  content_type TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','UPLOADED','FINALIZED')),
  sha256 TEXT,
  size_bytes BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  finalized_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS staged_upload_submission_idx ON publisher.staged_upload(submission_id, created_at DESC);
