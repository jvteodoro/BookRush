CREATE TABLE family.invite_address (
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    code_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (identity_issuer, identity_subject)
);

CREATE INDEX invite_address_expiry ON family.invite_address (expires_at);
