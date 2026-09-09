CREATE TABLE source_record (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
    external_id TEXT NOT NULL CHECK (btrim(external_id) <> ''),
    raw_metadata JSONB NOT NULL CHECK (jsonb_typeof(raw_metadata) = 'object'),
    retrieved_at TIMESTAMPTZ NOT NULL,
    content_hash VARCHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (id, source_id, external_id),
    UNIQUE (source_id, external_id, content_hash)
);

CREATE TABLE ingestion_job (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED')),
    trigger_type VARCHAR(24) NOT NULL CHECK (trigger_type IN ('MANUAL','SCHEDULED','RETRY')),
    items_discovered INTEGER NOT NULL DEFAULT 0 CHECK (items_discovered >= 0),
    items_processed INTEGER NOT NULL DEFAULT 0 CHECK (items_processed >= 0),
    items_succeeded INTEGER NOT NULL DEFAULT 0 CHECK (items_succeeded >= 0),
    items_failed INTEGER NOT NULL DEFAULT 0 CHECK (items_failed >= 0),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (id, source_id),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
    CHECK (items_processed = items_succeeded + items_failed AND items_processed <= items_discovered)
);

CREATE TABLE ingestion_item (
    id UUID PRIMARY KEY,
    ingestion_job_id UUID NOT NULL,
    source_id UUID NOT NULL,
    external_identifier TEXT NOT NULL CHECK (btrim(external_identifier) <> ''),
    attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number > 0),
    source_record_id UUID,
    book_id UUID REFERENCES book(id) ON DELETE RESTRICT,
    edition_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_code TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (ingestion_job_id, source_id) REFERENCES ingestion_job(id, source_id) ON DELETE RESTRICT,
    FOREIGN KEY (source_record_id, source_id, external_identifier)
        REFERENCES source_record(id, source_id, external_id) ON DELETE RESTRICT,
    FOREIGN KEY (edition_id, book_id) REFERENCES edition(id, book_id) ON DELETE RESTRICT,
    CHECK (edition_id IS NULL OR book_id IS NOT NULL),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
    UNIQUE (ingestion_job_id, external_identifier, attempt_number)
);

-- A version records the concrete ingestion attempt, not only the generic source.
ALTER TABLE book_asset_version ADD COLUMN ingestion_item_id UUID REFERENCES ingestion_item(id) ON DELETE RESTRICT;

CREATE INDEX ingestion_job_source_recent_idx ON ingestion_job(source_id, created_at DESC, id DESC);
CREATE INDEX ingestion_item_job_status_idx ON ingestion_item(ingestion_job_id, status);
CREATE INDEX ingestion_item_record_idx ON ingestion_item(source_record_id, source_id, external_identifier) WHERE source_record_id IS NOT NULL;
CREATE INDEX ingestion_item_book_idx ON ingestion_item(book_id) WHERE book_id IS NOT NULL;
CREATE INDEX ingestion_item_edition_idx ON ingestion_item(edition_id, book_id) WHERE edition_id IS NOT NULL;
CREATE INDEX asset_version_ingestion_idx ON book_asset_version(ingestion_item_id) WHERE ingestion_item_id IS NOT NULL;
CREATE TRIGGER ingestion_job_updated_at BEFORE UPDATE ON ingestion_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER ingestion_item_updated_at BEFORE UPDATE ON ingestion_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
