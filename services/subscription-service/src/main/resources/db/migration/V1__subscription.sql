CREATE TABLE subscription.plan (
    code text NOT NULL,
    version integer NOT NULL CHECK (version > 0),
    publish_books boolean NOT NULL,
    premium_family boolean NOT NULL,
    additional_seats integer NOT NULL CHECK (additional_seats >= 0),
    CHECK (premium_family OR additional_seats = 0),
    PRIMARY KEY (code, version)
);

CREATE TABLE subscription.contract (
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    plan_code text NOT NULL,
    plan_version integer NOT NULL,
    state text NOT NULL CHECK (state IN ('FREE','ACTIVE','CANCEL_AT_END','CANCELED','EXPIRED','SUSPENDED')),
    starts_at timestamptz,
    ends_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((state = 'FREE' AND starts_at IS NULL AND ends_at IS NULL)
        OR (state <> 'FREE' AND starts_at IS NOT NULL AND ends_at IS NOT NULL AND starts_at < ends_at)),
    PRIMARY KEY (identity_issuer, identity_subject),
    FOREIGN KEY (plan_code, plan_version) REFERENCES subscription.plan (code, version)
);

CREATE TABLE subscription.transition (
    event_id uuid PRIMARY KEY,
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    source_version bigint NOT NULL,
    agent_issuer text NOT NULL,
    agent_subject text NOT NULL,
    command jsonb NOT NULL,
    previous_state text NOT NULL,
    resulting_state text NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE (identity_issuer, identity_subject, source_version),
    FOREIGN KEY (identity_issuer, identity_subject) REFERENCES subscription.contract
);

CREATE TABLE subscription.outbox (
    event_id uuid PRIMARY KEY REFERENCES subscription.transition,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX subscription_outbox_pending ON subscription.outbox (created_at, event_id)
    WHERE published_at IS NULL;
