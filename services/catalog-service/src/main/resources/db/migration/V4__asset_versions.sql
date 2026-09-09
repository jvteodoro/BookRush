CREATE TABLE book_asset (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
    edition_id UUID,
    asset_type VARCHAR(24) NOT NULL CHECK (asset_type IN ('EPUB','PDF','TXT','HTML','COVER','THUMBNAIL','JSON','PARQUET','AUDIO','OTHER')),
    asset_role VARCHAR(24) NOT NULL CHECK (asset_role IN ('SOURCE','NORMALIZED','PUBLIC','PROCESSING','ANALYTICS','ML','COVER','DERIVED')),
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
    license_id UUID REFERENCES license(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT asset_edition_book FOREIGN KEY (edition_id, book_id) REFERENCES edition(id, book_id) ON DELETE RESTRICT
);

CREATE TABLE book_asset_version (
    id UUID PRIMARY KEY,
    book_asset_id UUID NOT NULL REFERENCES book_asset(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    storage_provider VARCHAR(24) NOT NULL CHECK (storage_provider = 'S3'),
    bucket TEXT NOT NULL CHECK (btrim(bucket) <> '' AND bucket = btrim(bucket)),
    object_key TEXT NOT NULL CHECK (btrim(object_key) <> ''),
    original_filename TEXT,
    content_type TEXT CHECK (btrim(content_type) <> ''),
    size_bytes BIGINT CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_UPLOAD'
        CHECK (status IN ('PENDING_UPLOAD','AVAILABLE','MISSING','FAILED','DELETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT asset_version_number UNIQUE (book_asset_id, version_number),
    CONSTRAINT asset_version_object UNIQUE (storage_provider, bucket, object_key),
    CONSTRAINT available_version_metadata CHECK (status <> 'AVAILABLE' OR
        (content_type IS NOT NULL AND size_bytes IS NOT NULL AND sha256 IS NOT NULL))
);

CREATE INDEX book_asset_book_idx ON book_asset(book_id);
CREATE INDEX book_asset_edition_idx ON book_asset(edition_id, book_id) WHERE edition_id IS NOT NULL;
CREATE INDEX book_asset_source_idx ON book_asset(source_id);
CREATE INDEX book_asset_license_idx ON book_asset(license_id) WHERE license_id IS NOT NULL;
CREATE INDEX asset_version_sha256_idx ON book_asset_version(sha256) WHERE sha256 IS NOT NULL;
CREATE INDEX asset_version_available_idx ON book_asset_version(book_asset_id, version_number DESC) WHERE status = 'AVAILABLE';

CREATE TRIGGER book_asset_updated_at BEFORE UPDATE ON book_asset
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER book_asset_version_updated_at BEFORE UPDATE ON book_asset_version
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
