ALTER TABLE book_asset_version
  ADD COLUMN IF NOT EXISTS availability_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
  ADD COLUMN IF NOT EXISTS current_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS verification_method VARCHAR(32);

ALTER TABLE book_asset_version
  ADD CONSTRAINT asset_version_availability_check CHECK
    (availability_status IN ('PENDING_UPLOAD','AVAILABLE','MISSING','CORRUPT','FAILED','DELETED')),
  ADD CONSTRAINT asset_version_verification_check CHECK
    (availability_status <> 'AVAILABLE' OR verified_at IS NOT NULL) NOT VALID;

UPDATE book_asset_version
SET availability_status = CASE status
      WHEN 'PENDING_UPLOAD' THEN 'PENDING_UPLOAD'
      WHEN 'MISSING' THEN 'MISSING'
      WHEN 'FAILED' THEN 'FAILED'
      WHEN 'DELETED' THEN 'DELETED'
      ELSE 'AVAILABLE'
    END,
    verified_at = CASE WHEN status = 'AVAILABLE' THEN COALESCE(verified_at, created_at) ELSE verified_at END,
    verification_method = CASE WHEN status = 'AVAILABLE' THEN COALESCE(verification_method, 'LEGACY_MIGRATION') ELSE verification_method END;
ALTER TABLE book_asset_version VALIDATE CONSTRAINT asset_version_verification_check;

ALTER TABLE book_asset
  ADD COLUMN IF NOT EXISTS current_version_id UUID;
ALTER TABLE book_asset_version
  ADD CONSTRAINT asset_version_id_asset_unique UNIQUE (id, book_asset_id);
ALTER TABLE book_asset
  ADD CONSTRAINT book_asset_current_version_fk
    FOREIGN KEY (current_version_id, id)
    REFERENCES book_asset_version(id, book_asset_id) ON DELETE RESTRICT;

CREATE TABLE storage_intent (
    id UUID PRIMARY KEY,
    operation_key TEXT NOT NULL CHECK (btrim(operation_key) <> ''),
    asset_id UUID REFERENCES book_asset(id) ON DELETE RESTRICT,
    asset_version_id UUID REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    storage_provider VARCHAR(32) NOT NULL,
    bucket TEXT NOT NULL CHECK (btrim(bucket) <> ''),
    object_key TEXT NOT NULL CHECK (btrim(object_key) <> ''),
    expected_sha256 VARCHAR(64) CHECK (expected_sha256 IS NULL OR expected_sha256 ~ '^[0-9a-f]{64}$'),
    state VARCHAR(24) NOT NULL DEFAULT 'PLANNED'
      CHECK (state IN ('PLANNED','UPLOADING','UPLOADED','VERIFIED','COMMITTED','RETRY_WAIT','FAILED','DELETE_CANDIDATE','DELETED')),
    fence_token BIGINT NOT NULL DEFAULT 0 CHECK (fence_token >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (operation_key),
    UNIQUE (storage_provider, bucket, object_key)
);
CREATE INDEX storage_intent_state_idx ON storage_intent(state, next_attempt_at, created_at);
CREATE INDEX storage_intent_asset_idx ON storage_intent(asset_id, state) WHERE asset_id IS NOT NULL;

CREATE TABLE processing_input (
    processing_id UUID NOT NULL REFERENCES asset_processing(id) ON DELETE RESTRICT,
    input_order INTEGER NOT NULL CHECK (input_order >= 0),
    asset_version_id UUID NOT NULL REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (processing_id, input_order),
    UNIQUE (processing_id, asset_version_id)
);

CREATE TABLE processing_output (
    processing_id UUID NOT NULL REFERENCES asset_processing(id) ON DELETE RESTRICT,
    output_order INTEGER NOT NULL CHECK (output_order >= 0),
    asset_version_id UUID NOT NULL REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (processing_id, output_order),
    UNIQUE (processing_id, asset_version_id)
);

INSERT INTO processing_input(processing_id, input_order, asset_version_id, sha256)
SELECT p.id, 0, p.input_asset_version_id, v.sha256
FROM asset_processing p JOIN book_asset_version v ON v.id = p.input_asset_version_id
ON CONFLICT DO NOTHING;
INSERT INTO processing_output(processing_id, output_order, asset_version_id, sha256)
SELECT p.id, 0, p.output_asset_version_id, v.sha256
FROM asset_processing p JOIN book_asset_version v ON v.id = p.output_asset_version_id
WHERE p.output_asset_version_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX processing_input_version_idx ON processing_input(asset_version_id);
CREATE INDEX processing_output_version_idx ON processing_output(asset_version_id);
CREATE TRIGGER storage_intent_updated_at BEFORE UPDATE ON storage_intent
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
