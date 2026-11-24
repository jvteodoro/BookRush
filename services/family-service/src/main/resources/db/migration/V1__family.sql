CREATE TABLE family.household (
    id uuid PRIMARY KEY,
    owner_issuer text NOT NULL,
    owner_subject text NOT NULL,
    created_at timestamptz NOT NULL,
    event_version bigint NOT NULL DEFAULT 0 CHECK (event_version >= 0),
    UNIQUE (owner_issuer, owner_subject)
);

CREATE TABLE family.membership (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES family.household,
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    state text NOT NULL CHECK (state IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- Suspended memberships retain a slot and may not join another household.
CREATE UNIQUE INDEX membership_current_identity
    ON family.membership (identity_issuer, identity_subject) WHERE state IN ('ACTIVE', 'SUSPENDED');
CREATE INDEX membership_household ON family.membership (household_id, state);

CREATE TABLE family.member_permission (
    membership_id uuid PRIMARY KEY REFERENCES family.membership,
    can_publish boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE family.invitation (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES family.household,
    recipient_issuer text NOT NULL,
    recipient_subject text NOT NULL,
    state text NOT NULL CHECK (state IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED', 'EXPIRED')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    updated_at timestamptz NOT NULL,
    request_id uuid NOT NULL,
    recipient_code_hash char(64) NOT NULL,
    UNIQUE (household_id, request_id)
);

CREATE UNIQUE INDEX invitation_pending_recipient
    ON family.invitation (household_id, recipient_issuer, recipient_subject) WHERE state = 'PENDING';
CREATE INDEX invitation_recipient ON family.invitation (recipient_issuer, recipient_subject, expires_at);
CREATE INDEX invitation_expiry ON family.invitation (expires_at, household_id) WHERE state = 'PENDING';

CREATE TABLE family.audit (
    event_id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES family.household,
    resource_id uuid NOT NULL,
    event_type text NOT NULL,
    actor_issuer text,
    actor_subject text,
    occurred_at timestamptz NOT NULL,
    CHECK ((actor_issuer IS NULL) = (actor_subject IS NULL))
);

CREATE TABLE family.outbox (
    event_id uuid PRIMARY KEY REFERENCES family.audit,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX family_outbox_pending ON family.outbox (created_at, event_id) WHERE published_at IS NULL;
