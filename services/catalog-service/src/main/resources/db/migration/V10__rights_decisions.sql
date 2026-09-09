CREATE TABLE rights_decision (
    id UUID PRIMARY KEY,
    edition_id UUID REFERENCES edition(id) ON DELETE RESTRICT,
    asset_id UUID REFERENCES book_asset(id) ON DELETE RESTRICT,
    action VARCHAR(24) NOT NULL CHECK (action IN ('DOWNLOAD','PROCESSING','DISTRIBUTION')),
    territory VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' CHECK (btrim(territory) <> ''),
    distribution_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN'
      CHECK (distribution_status IN ('UNKNOWN','REVIEW_REQUIRED','APPROVED','REJECTED','REVOKED')),
    evidence_reference TEXT,
    evidence_sha256 VARCHAR(64) CHECK (evidence_sha256 IS NULL OR evidence_sha256 ~ '^[0-9a-f]{64}$'),
    policy_code TEXT NOT NULL CHECK (btrim(policy_code) <> ''),
    actor TEXT NOT NULL CHECK (btrim(actor) <> ''),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (num_nonnulls(edition_id, asset_id) = 1),
    CHECK (valid_until IS NULL OR (valid_from IS NOT NULL AND valid_until > valid_from))
);
CREATE INDEX rights_decision_edition_idx ON rights_decision(edition_id, action, territory, created_at DESC)
  WHERE edition_id IS NOT NULL;
CREATE INDEX rights_decision_asset_idx ON rights_decision(asset_id, action, territory, created_at DESC)
  WHERE asset_id IS NOT NULL;
CREATE INDEX rights_decision_status_idx ON rights_decision(distribution_status, action, valid_until);
