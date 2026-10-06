CREATE TABLE IF NOT EXISTS admin.moderation_case (
  id UUID PRIMARY KEY,
  report_id UUID,
  target_type VARCHAR(16) NOT NULL
    CHECK (target_type IN ('COMMENT', 'BOOK', 'PUBLICATION', 'PROFILE')),
  target_id UUID NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
  severity VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
    CHECK (severity IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
  opened_by_subject TEXT NOT NULL,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ,
  CHECK ((status IN ('RESOLVED', 'DISMISSED')) = (resolved_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS admin.moderation_action (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES admin.moderation_case(id) ON DELETE RESTRICT,
  decision VARCHAR(24) NOT NULL
    CHECK (decision IN ('NO_ACTION', 'HIDE', 'REMOVE', 'WARN', 'RESTRICT', 'RESTORE')),
  reason_code VARCHAR(80) NOT NULL,
  actor_subject TEXT NOT NULL,
  evidence JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(evidence) = 'object'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS admin.user_restriction (
  id UUID PRIMARY KEY,
  target_subject_key TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED', 'FAILED')),
  reason_code VARCHAR(80) NOT NULL,
  actor_subject TEXT NOT NULL,
  keycloak_command_key VARCHAR(160) NOT NULL UNIQUE,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  details JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(details) = 'object'),
  CHECK (expires_at IS NULL OR expires_at > requested_at),
  CHECK (confirmed_at IS NULL OR status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS moderation_case_target_idx
  ON admin.moderation_case(target_type, target_id, opened_at DESC);
CREATE INDEX IF NOT EXISTS moderation_case_status_idx
  ON admin.moderation_case(status, opened_at DESC)
  WHERE status IN ('OPEN', 'IN_REVIEW');
CREATE INDEX IF NOT EXISTS user_restriction_subject_idx
  ON admin.user_restriction(target_subject_key, requested_at DESC);
