CREATE TABLE reporting.release_event (
  id UUID PRIMARY KEY,
  event_id VARCHAR(220) NOT NULL UNIQUE,
  job_name VARCHAR(180) NOT NULL,
  build_number BIGINT NOT NULL CHECK (build_number > 0),
  build_url VARCHAR(1024),
  branch VARCHAR(180),
  git_commit VARCHAR(64),
  event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('BUILD', 'DEPLOY', 'ROLLBACK')),
  status VARCHAR(16) NOT NULL CHECK (status IN ('STARTED', 'SUCCESS', 'FAILURE', 'UNSTABLE', 'ABORTED')),
  service_name VARCHAR(120),
  image_ref VARCHAR(512),
  image_digest VARCHAR(71) CHECK (image_digest IS NULL OR image_digest ~ '^sha256:[0-9a-f]{64}$'),
  duration_ms BIGINT CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 86400000),
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT reporting_release_event_service_type_ck CHECK (
    (event_type = 'BUILD' AND service_name IS NULL)
    OR (event_type IN ('DEPLOY', 'ROLLBACK') AND service_name IS NOT NULL)
  )
);

CREATE INDEX reporting_release_event_time_idx
  ON reporting.release_event(occurred_at DESC, id DESC);
CREATE INDEX reporting_release_event_job_idx
  ON reporting.release_event(job_name, build_number DESC, occurred_at DESC);
