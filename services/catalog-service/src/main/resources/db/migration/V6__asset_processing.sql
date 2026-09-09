CREATE TABLE asset_processing (
    id UUID PRIMARY KEY,
    input_asset_version_id UUID NOT NULL REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    output_asset_version_id UUID REFERENCES book_asset_version(id) ON DELETE RESTRICT,
    processing_type VARCHAR(64) NOT NULL CHECK (processing_type ~ '^[A-Z][A-Z0-9_]*$'),
    processor TEXT NOT NULL CHECK (btrim(processor) <> ''),
    processor_version TEXT NOT NULL CHECK (btrim(processor_version) <> ''),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(metadata) = 'object'),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (output_asset_version_id IS NULL OR output_asset_version_id <> input_asset_version_id),
    CHECK (status <> 'SUCCEEDED' OR output_asset_version_id IS NOT NULL),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at))
);
CREATE INDEX processing_input_idx ON asset_processing(input_asset_version_id);
CREATE INDEX processing_output_idx ON asset_processing(output_asset_version_id) WHERE output_asset_version_id IS NOT NULL;
CREATE TRIGGER asset_processing_updated_at BEFORE UPDATE ON asset_processing
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
