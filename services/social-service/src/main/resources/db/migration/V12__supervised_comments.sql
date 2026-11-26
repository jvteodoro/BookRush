CREATE INDEX comments_supervised_history ON social.comments(subject_key, created_at DESC, id DESC)
    WHERE author_deleted_at IS NULL;

CREATE TABLE social.supervised_comments_cursor (
    id UUID PRIMARY KEY,
    guardian_key TEXT NOT NULL,
    target_key TEXT NOT NULL,
    member_id UUID NOT NULL,
    scope TEXT NOT NULL CHECK (scope = 'COMMENTS'),
    grant_version BIGINT NOT NULL,
    upper_bound TIMESTAMPTZ NOT NULL,
    last_at TIMESTAMPTZ NOT NULL,
    last_book UUID NOT NULL,
    last_event UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX supervised_comments_cursor_expiry ON social.supervised_comments_cursor(expires_at);

CREATE TABLE social.supervised_comments_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guardian_key TEXT NOT NULL,
    target_key TEXT,
    member_id UUID NOT NULL,
    scope TEXT NOT NULL CHECK (scope = 'COMMENTS'),
    outcome INTEGER NOT NULL,
    correlation_id TEXT NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX supervised_comments_audit_expiry ON social.supervised_comments_audit(accessed_at);
