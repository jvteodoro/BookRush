ALTER TABLE source_record
  ADD COLUMN IF NOT EXISTS record_type VARCHAR(32) NOT NULL DEFAULT 'DOMAIN_RECORD',
  ADD COLUMN IF NOT EXISTS revision TEXT,
  ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS first_retrieved_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  ADD COLUMN IF NOT EXISTS raw_sha256 VARCHAR(64),
  ADD COLUMN IF NOT EXISTS semantic_sha256 VARCHAR(64),
  ADD COLUMN IF NOT EXISTS raw_locator JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS small_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS anomaly_code TEXT;

ALTER TABLE source_record
  ADD CONSTRAINT source_record_type_check CHECK (record_type ~ '^[A-Z][A-Z0-9_]*$'),
  ADD CONSTRAINT source_record_raw_sha256_check CHECK (raw_sha256 IS NULL OR raw_sha256 ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT source_record_semantic_sha256_check CHECK (semantic_sha256 IS NULL OR semantic_sha256 ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT source_record_locator_object_check CHECK (jsonb_typeof(raw_locator) = 'object'),
  ADD CONSTRAINT source_record_small_metadata_object_check CHECK (jsonb_typeof(small_metadata) = 'object');

UPDATE source_record
SET raw_sha256 = content_hash,
    semantic_sha256 = content_hash
WHERE raw_sha256 IS NULL;

CREATE TABLE source_record_observation (
    id UUID PRIMARY KEY,
    source_record_id UUID NOT NULL REFERENCES source_record(id) ON DELETE RESTRICT,
    snapshot_id UUID,
    observed_revision TEXT,
    observed_at TIMESTAMPTZ NOT NULL,
    raw_sha256 VARCHAR(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    semantic_sha256 VARCHAR(64) CHECK (semantic_sha256 IS NULL OR semantic_sha256 ~ '^[0-9a-f]{64}$'),
    raw_locator JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_position TEXT,
    anomaly_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (jsonb_typeof(raw_locator) = 'object'),
    UNIQUE (source_record_id, observed_revision, raw_sha256)
);
CREATE INDEX source_record_observation_snapshot_idx
  ON source_record_observation(snapshot_id, observed_at DESC)
  WHERE snapshot_id IS NOT NULL;
CREATE INDEX source_record_observation_hash_idx
  ON source_record_observation(raw_sha256);

CREATE TABLE field_provenance (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL CHECK (entity_type ~ '^[A-Z][A-Z0-9_]*$'),
    entity_id UUID NOT NULL,
    field_name TEXT NOT NULL CHECK (btrim(field_name) <> ''),
    value JSONB,
    value_reference TEXT,
    source_id UUID REFERENCES source(id) ON DELETE RESTRICT,
    source_record_id UUID REFERENCES source_record(id) ON DELETE RESTRICT,
    rule_code TEXT NOT NULL CHECK (btrim(rule_code) <> ''),
    confidence NUMERIC(5,4) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    decision VARCHAR(32) NOT NULL DEFAULT 'APPLIED'
      CHECK (decision IN ('APPLIED','REJECTED','REVIEW_REQUIRED','SUPERSEDED')),
    previous_value JSONB,
    actor TEXT,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (value IS NOT NULL OR value_reference IS NOT NULL OR decision <> 'APPLIED'),
    CHECK (source_id IS NOT NULL OR source_record_id IS NOT NULL OR actor IS NOT NULL)
);
CREATE INDEX field_provenance_entity_idx
  ON field_provenance(entity_type, entity_id, field_name, observed_at DESC);
CREATE INDEX field_provenance_source_idx
  ON field_provenance(source_id, source_record_id, observed_at DESC);

ALTER TABLE external_identifier DROP CONSTRAINT IF EXISTS external_identifier_target_type;
ALTER TABLE external_identifier DROP CONSTRAINT IF EXISTS external_identifier_value_format;
ALTER TABLE external_identifier
  DROP CONSTRAINT IF EXISTS external_identifier_identifier_type_check;
ALTER TABLE external_identifier
  ADD CONSTRAINT external_identifier_identifier_type_check CHECK (identifier_type IN (
    'ISBN10', 'ISBN13', 'GUTENBERG_ID', 'OPEN_LIBRARY_WORK_ID',
    'OPEN_LIBRARY_EDITION_ID', 'OPEN_LIBRARY_AUTHOR_ID', 'INTERNET_ARCHIVE_ID',
    'DOI', 'LCCN', 'OCLC', 'WIKIDATA_ID'));
ALTER TABLE external_identifier
  ADD CONSTRAINT external_identifier_target_type_v8 CHECK (
    (identifier_type IN ('ISBN10', 'ISBN13', 'GUTENBERG_ID', 'OPEN_LIBRARY_EDITION_ID', 'INTERNET_ARCHIVE_ID', 'LCCN', 'OCLC') AND edition_id IS NOT NULL)
    OR (identifier_type = 'OPEN_LIBRARY_WORK_ID' AND book_id IS NOT NULL)
    OR (identifier_type = 'OPEN_LIBRARY_AUTHOR_ID' AND author_id IS NOT NULL)
    OR (identifier_type = 'DOI' AND author_id IS NULL)
    OR identifier_type = 'WIKIDATA_ID');

CREATE OR REPLACE FUNCTION catalog.is_valid_isbn10(value TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE total INTEGER := 0; digit INTEGER; i INTEGER;
BEGIN
  IF value IS NULL OR value !~ '^[0-9]{9}[0-9X]$' THEN RETURN FALSE; END IF;
  FOR i IN 1..10 LOOP
    digit := CASE WHEN substr(value, i, 1) = 'X' THEN 10 ELSE substr(value, i, 1)::INTEGER END;
    total := total + digit * (11 - i);
  END LOOP;
  RETURN total % 11 = 0;
END $$;

CREATE OR REPLACE FUNCTION catalog.is_valid_isbn13(value TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE total INTEGER := 0; i INTEGER;
BEGIN
  IF value IS NULL OR value !~ '^[0-9]{13}$' THEN RETURN FALSE; END IF;
  FOR i IN 1..12 LOOP
    total := total + substr(value, i, 1)::INTEGER * CASE WHEN i % 2 = 1 THEN 1 ELSE 3 END;
  END LOOP;
  RETURN (10 - total % 10) % 10 = substr(value, 13, 1)::INTEGER;
END $$;

ALTER TABLE external_identifier
  ADD CONSTRAINT external_identifier_value_format_v8 CHECK (
    CASE identifier_type
      WHEN 'ISBN10' THEN catalog.is_valid_isbn10(identifier_value)
      WHEN 'ISBN13' THEN catalog.is_valid_isbn13(identifier_value)
      WHEN 'GUTENBERG_ID' THEN identifier_value ~ '^[1-9][0-9]*$'
      WHEN 'OPEN_LIBRARY_WORK_ID' THEN identifier_value ~ '^OL[1-9][0-9]*W$'
      WHEN 'OPEN_LIBRARY_EDITION_ID' THEN identifier_value ~ '^OL[1-9][0-9]*M$'
      WHEN 'OPEN_LIBRARY_AUTHOR_ID' THEN identifier_value ~ '^OL[1-9][0-9]*A$'
      WHEN 'OCLC' THEN identifier_value ~ '^[1-9][0-9]*$'
      WHEN 'WIKIDATA_ID' THEN identifier_value ~ '^Q[1-9][0-9]*$'
      WHEN 'DOI' THEN identifier_value ~ '^10\.[0-9]{4,9}/[^[:space:]]+$' AND identifier_value = lower(identifier_value)
      WHEN 'INTERNET_ARCHIVE_ID' THEN identifier_value !~ '[[:space:]/]'
      ELSE identifier_value !~ '[[:space:]]'
    END);
