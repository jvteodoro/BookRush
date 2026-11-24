CREATE TABLE subscription.billing_inbox (
    event_id uuid PRIMARY KEY,
    billing_source varchar(64) NOT NULL,
    external_event_id varchar(160) NOT NULL,
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    agent_issuer text NOT NULL,
    agent_subject text NOT NULL,
    source_version bigint NOT NULL CHECK (source_version > 0),
    operation text NOT NULL,
    starts_at timestamptz,
    ends_at timestamptz,
    received_at timestamptz NOT NULL,
    applied_at timestamptz,
    state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'APPLIED', 'STALE')),
    CHECK ((state = 'APPLIED') = (applied_at IS NOT NULL)),
    UNIQUE (billing_source, external_event_id)
);

CREATE INDEX billing_inbox_pending ON subscription.billing_inbox (received_at, event_id)
    WHERE state = 'PENDING';
