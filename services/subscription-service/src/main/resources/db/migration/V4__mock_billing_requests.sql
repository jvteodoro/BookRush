CREATE TABLE subscription.mock_billing_request (
    identity_issuer text NOT NULL,
    identity_subject text NOT NULL,
    request_id uuid NOT NULL,
    external_event_id uuid NOT NULL UNIQUE,
    scenario text NOT NULL CHECK (scenario IN ('APPROVE','DECLINE','RENEW','CANCEL','REFUND','CHARGEBACK')),
    source_version bigint,
    operation text,
    starts_at timestamptz,
    ends_at timestamptz,
    created_at timestamptz NOT NULL,
    CHECK ((scenario = 'DECLINE' AND source_version IS NULL AND operation IS NULL)
        OR (scenario <> 'DECLINE' AND source_version IS NOT NULL AND source_version > 0 AND operation IS NOT NULL)),
    PRIMARY KEY (identity_issuer, identity_subject, request_id)
);
