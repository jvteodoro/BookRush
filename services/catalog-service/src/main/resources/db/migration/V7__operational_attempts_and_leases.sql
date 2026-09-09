-- Evolução aditiva do V5. O catalog-service continua sendo o único executor
-- destas migrations; os registros históricos não são reescritos nem removidos.

ALTER TABLE ingestion_job
    ADD COLUMN IF NOT EXISTS parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS parameter_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS requested_by TEXT,
    ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMPTZ;

DO $$
DECLARE c RECORD;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'catalog.ingestion_job'::regclass
      AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%status%'
  LOOP
    EXECUTE format('ALTER TABLE catalog.ingestion_job DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE ingestion_job
  ADD CONSTRAINT ingestion_job_status_check CHECK
    (status IN ('PENDING','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED',
                'CANCELLED','CANCEL_REQUESTED','PAUSED'));
ALTER TABLE ingestion_job
  ADD CONSTRAINT ingestion_job_parameters_object_check
    CHECK (jsonb_typeof(parameters) = 'object');
ALTER TABLE ingestion_job
  ADD CONSTRAINT ingestion_job_fingerprint_check
    CHECK (parameter_fingerprint IS NULL OR parameter_fingerprint ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX IF NOT EXISTS ingestion_job_source_fingerprint_uq
  ON ingestion_job(source_id, parameter_fingerprint)
  WHERE parameter_fingerprint IS NOT NULL;

CREATE TABLE ingestion_job_attempt (
    id UUID PRIMARY KEY,
    ingestion_job_id UUID NOT NULL REFERENCES ingestion_job(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    batch_job_execution_id BIGINT,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
      CHECK (status IN ('PENDING','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED','PAUSED')),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (ingestion_job_id, attempt_number),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at))
);

ALTER TABLE ingestion_item
  ADD COLUMN IF NOT EXISTS item_key TEXT;
ALTER TABLE ingestion_item

-- V5 stores attempts as rows and therefore permits the same external item more
-- than once. Keep those rows intact and introduce a logical item owner instead
-- of adding an invalid unique index over historical attempts.
CREATE TABLE ingestion_logical_item (
    id UUID PRIMARY KEY,
    ingestion_job_id UUID NOT NULL REFERENCES ingestion_job(id) ON DELETE RESTRICT,
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
    item_key TEXT NOT NULL CHECK (btrim(item_key) <> ''),
    external_identifier TEXT NOT NULL CHECK (btrim(external_identifier) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (ingestion_job_id, item_key),
    UNIQUE (id, ingestion_job_id)
);
ALTER TABLE ingestion_item ADD COLUMN logical_item_id UUID;
INSERT INTO ingestion_logical_item(id, ingestion_job_id, source_id, item_key, external_identifier)
SELECT md5(i.ingestion_job_id::text || ':' || i.source_id::text || ':' || i.external_identifier)::uuid,
       s.code || ':' || i.external_identifier, i.external_identifier
FROM ingestion_item i JOIN source s ON s.id = i.source_id
GROUP BY i.ingestion_job_id, i.source_id, s.code, i.external_identifier;
UPDATE ingestion_item i
SET item_key = l.item_key, logical_item_id = l.id
FROM source s
JOIN ingestion_logical_item l
  ON l.ingestion_job_id = i.ingestion_job_id
 AND l.source_id = i.source_id
 AND l.external_identifier = i.external_identifier
WHERE s.id = i.source_id;
ALTER TABLE ingestion_item
  ADD CONSTRAINT ingestion_item_logical_fk FOREIGN KEY (logical_item_id)
    REFERENCES ingestion_logical_item(id) ON DELETE RESTRICT;
CREATE INDEX ingestion_item_logical_idx ON ingestion_item(logical_item_id);

CREATE TABLE ingestion_task (
    id UUID PRIMARY KEY,
    ingestion_job_id UUID NOT NULL REFERENCES ingestion_job(id) ON DELETE RESTRICT,
    ingestion_item_id UUID REFERENCES ingestion_item(id) ON DELETE RESTRICT,
    operation_key TEXT NOT NULL CHECK (btrim(operation_key) <> ''),
    task_type VARCHAR(64) NOT NULL CHECK (task_type ~ '^[A-Z][A-Z0-9_]*$'),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
      CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','NOOP','SKIPPED','QUARANTINED',
                        'FAILED','RETRY_WAIT','CANCELLED')),
    reason_code TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    lease_until TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    fence_token BIGINT NOT NULL DEFAULT 0 CHECK (fence_token >= 0),
    claimed_by TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (operation_key),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
    CHECK (status <> 'RUNNING' OR lease_until IS NOT NULL)
);

CREATE TABLE ingestion_task_attempt (
    id UUID PRIMARY KEY,
    ingestion_task_id UUID NOT NULL REFERENCES ingestion_task(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    fence_token BIGINT NOT NULL CHECK (fence_token >= 0),
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING'
      CHECK (status IN ('RUNNING','SUCCEEDED','NOOP','SKIPPED','QUARANTINED','FAILED','CANCELLED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    finished_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    reason_code TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (ingestion_task_id, attempt_number),
    CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE TABLE ingestion_command_result (
    id UUID PRIMARY KEY,
    principal TEXT NOT NULL CHECK (btrim(principal) <> ''),
    endpoint TEXT NOT NULL CHECK (btrim(endpoint) <> ''),
    idempotency_key TEXT NOT NULL CHECK (btrim(idempotency_key) <> ''),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    response_status INTEGER NOT NULL CHECK (response_status BETWEEN 100 AND 599),
    response_body JSONB NOT NULL CHECK (jsonb_typeof(response_body) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (principal, endpoint, idempotency_key)
);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor TEXT NOT NULL CHECK (btrim(actor) <> ''),
    action TEXT NOT NULL CHECK (btrim(action) <> ''),
    entity_type TEXT NOT NULL CHECK (btrim(entity_type) <> ''),
    entity_id UUID,
    correlation_id TEXT,
    before_state JSONB,
    after_state JSONB,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (before_state IS NULL OR jsonb_typeof(before_state) = 'object'),
    CHECK (after_state IS NULL OR jsonb_typeof(after_state) = 'object')
);

CREATE INDEX ingestion_job_attempt_job_idx ON ingestion_job_attempt(ingestion_job_id, attempt_number DESC);
CREATE INDEX ingestion_task_claim_idx ON ingestion_task(status, next_attempt_at, created_at, id)
  WHERE status IN ('PENDING','RETRY_WAIT');
CREATE INDEX ingestion_task_lease_idx ON ingestion_task(lease_until, id)
  WHERE status = 'RUNNING';
CREATE INDEX ingestion_task_item_idx ON ingestion_task(ingestion_item_id) WHERE ingestion_item_id IS NOT NULL;
CREATE INDEX ingestion_task_attempt_task_idx ON ingestion_task_attempt(ingestion_task_id, attempt_number DESC);
CREATE INDEX audit_event_entity_idx ON audit_event(entity_type, entity_id, created_at DESC);
CREATE TRIGGER ingestion_task_updated_at BEFORE UPDATE ON ingestion_task
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
