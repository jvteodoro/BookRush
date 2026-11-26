CREATE TABLE family.supervision_grant (
    id uuid PRIMARY KEY,
    guardian_issuer text NOT NULL,
    guardian_subject text NOT NULL,
    minor_issuer text NOT NULL,
    minor_subject text NOT NULL,
    request_id uuid NOT NULL,
    requested_scopes text[] NOT NULL,
    scopes text[] NOT NULL DEFAULT '{}',
    state text NOT NULL CHECK (state IN ('REQUESTED','PENDING_VERIFICATION','ACTIVE','SUSPENDED','REJECTED','REVOKED','EXPIRED')),
    effective_from timestamptz,
    effective_until timestamptz,
    verification_ref uuid,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (guardian_issuer, guardian_subject, request_id),
    CHECK ((guardian_issuer, guardian_subject) <> (minor_issuer, minor_subject)),
    CHECK (cardinality(requested_scopes)>0 AND requested_scopes <@ ARRAY['PRODUCT_ACCESS','BOOKS_ACCESSED','READING_ACTIVITY','COMMENTS']),
    CHECK (scopes <@ requested_scopes),
    CHECK ((effective_from IS NULL) = (effective_until IS NULL)),
    CHECK (effective_from IS NULL OR effective_from < effective_until),
    CHECK (state <> 'ACTIVE' OR (effective_from IS NOT NULL AND verification_ref IS NOT NULL AND cardinality(scopes)>0))
);
CREATE UNIQUE INDEX supervision_current_pair ON family.supervision_grant
  (guardian_issuer, guardian_subject, minor_issuer, minor_subject)
  WHERE state IN ('REQUESTED','PENDING_VERIFICATION','ACTIVE','SUSPENDED');
CREATE INDEX supervision_current_minor ON family.supervision_grant(minor_issuer, minor_subject)
  WHERE state = 'ACTIVE';
CREATE INDEX supervision_expiry ON family.supervision_grant(effective_until)
  WHERE state IN ('ACTIVE','SUSPENDED');

CREATE TABLE family.supervision_command (
    grant_id uuid NOT NULL REFERENCES family.supervision_grant,
    request_id uuid NOT NULL,
    fingerprint char(64) NOT NULL,
    version bigint NOT NULL,
    state text NOT NULL,
    PRIMARY KEY (grant_id, request_id)
);
CREATE TABLE family.supervision_audit (
    event_id uuid PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES family.supervision_grant,
    actor_issuer text,
    actor_subject text,
    event_type text NOT NULL,
    version bigint NOT NULL,
    simulated boolean NOT NULL,
    occurred_at timestamptz NOT NULL,
    UNIQUE (grant_id, version)
);
CREATE INDEX supervision_audit_retention ON family.supervision_audit(occurred_at);
CREATE TABLE family.supervision_outbox (
    event_id uuid PRIMARY KEY,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);
CREATE INDEX supervision_outbox_pending ON family.supervision_outbox(created_at,event_id)
  WHERE published_at IS NULL;
