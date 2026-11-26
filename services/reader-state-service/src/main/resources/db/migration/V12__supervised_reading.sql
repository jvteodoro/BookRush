CREATE TABLE reader_state.book_open (
    subject_key TEXT NOT NULL,
    book_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    request_id UUID NOT NULL,
    PRIMARY KEY (subject_key, book_id, request_id)
);
CREATE INDEX book_open_history ON reader_state.book_open(subject_key, occurred_at DESC, book_id, request_id);
CREATE INDEX session_supervised_history ON reader_state.session(subject_key, ended_at) WHERE active_seconds > 0;

-- Distinct endpoints are the only timestamps preserved by the legacy table.
-- An equal pair may be a synthetic V9 backfill from progress, so omit it.
INSERT INTO reader_state.book_open(subject_key, book_id, occurred_at, request_id)
SELECT subject_key, book_id, occurred_at, gen_random_uuid()
FROM reader_state.seen_book
CROSS JOIN LATERAL (VALUES (first_seen_at), (last_seen_at)) AS evidence(occurred_at)
WHERE first_seen_at < last_seen_at AND occurred_at >= now() - interval '90 days'
  AND occurred_at > (SELECT installed_on AT TIME ZONE current_setting('TimeZone')
                     FROM reader_state.flyway_schema_history WHERE version='9');

CREATE TABLE reader_state.supervised_reading_cursor (
    id UUID PRIMARY KEY,
    guardian_key TEXT NOT NULL,
    member_id UUID NOT NULL,
    scope TEXT NOT NULL,
    grant_version BIGINT NOT NULL,
    upper_bound TIMESTAMPTZ NOT NULL,
    last_at TIMESTAMPTZ NOT NULL,
    last_book UUID NOT NULL,
    last_event UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX supervised_reading_cursor_expiry ON reader_state.supervised_reading_cursor(expires_at);

CREATE TABLE reader_state.supervised_reading_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guardian_key TEXT NOT NULL,
    member_id UUID NOT NULL,
    scope TEXT NOT NULL,
    outcome INTEGER NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX supervised_reading_audit_expiry ON reader_state.supervised_reading_audit(accessed_at);
