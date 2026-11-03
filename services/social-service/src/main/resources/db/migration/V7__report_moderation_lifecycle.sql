ALTER TABLE social.reports
  ADD COLUMN IF NOT EXISTS reviewed_by_subject TEXT,
  ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS resolution_note VARCHAR(200);

UPDATE social.reports
SET status = 'OPEN'
WHERE status NOT IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED');

ALTER TABLE social.reports
  DROP CONSTRAINT IF EXISTS social_reports_status_check;

ALTER TABLE social.reports
  ADD CONSTRAINT social_reports_status_check
  CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED'));

CREATE INDEX IF NOT EXISTS social_reports_status_created_idx
  ON social.reports(status, created_at DESC);
