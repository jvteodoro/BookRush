CREATE TABLE source (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    base_url TEXT,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN (
        'DIGITAL_LIBRARY', 'EXTERNAL_CATALOG', 'ADMIN_UPLOAD', 'INTERNAL_PIPELINE')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE license (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    url TEXT,
    commercial_use_allowed BOOLEAN,
    modification_allowed BOOLEAN,
    redistribution_allowed BOOLEAN,
    attribution_required BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Absence of a recorded license is legitimate and does not grant distribution rights.
ALTER TABLE edition ADD COLUMN license_id UUID REFERENCES license(id) ON DELETE RESTRICT;
CREATE INDEX edition_license_idx ON edition(license_id) WHERE license_id IS NOT NULL;

CREATE TABLE external_identifier (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
    book_id UUID REFERENCES book(id) ON DELETE RESTRICT,
    edition_id UUID REFERENCES edition(id) ON DELETE RESTRICT,
    author_id UUID REFERENCES author(id) ON DELETE RESTRICT,
    identifier_type VARCHAR(32) NOT NULL CHECK (identifier_type IN (
        'ISBN10', 'ISBN13', 'GUTENBERG_ID', 'OPEN_LIBRARY_WORK_ID',
        'OPEN_LIBRARY_EDITION_ID', 'INTERNET_ARCHIVE_ID', 'DOI', 'LCCN', 'OCLC', 'WIKIDATA_ID')),
    identifier_value TEXT NOT NULL CHECK (identifier_value <> '' AND identifier_value = btrim(identifier_value)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT external_identifier_one_target CHECK (num_nonnulls(book_id, edition_id, author_id) = 1),
    CONSTRAINT external_identifier_scope UNIQUE (source_id, identifier_type, identifier_value),
    CONSTRAINT external_identifier_target_type CHECK (
        (identifier_type IN ('ISBN10', 'ISBN13', 'GUTENBERG_ID', 'OPEN_LIBRARY_EDITION_ID', 'INTERNET_ARCHIVE_ID', 'LCCN', 'OCLC') AND edition_id IS NOT NULL)
        OR (identifier_type = 'OPEN_LIBRARY_WORK_ID' AND book_id IS NOT NULL)
        OR (identifier_type = 'DOI' AND author_id IS NULL)
        OR identifier_type = 'WIKIDATA_ID'),
    CONSTRAINT external_identifier_value_format CHECK (
        CASE identifier_type
            WHEN 'ISBN10' THEN identifier_value ~ '^[0-9]{9}[0-9X]$'
            WHEN 'ISBN13' THEN identifier_value ~ '^[0-9]{13}$'
            WHEN 'GUTENBERG_ID' THEN identifier_value ~ '^[1-9][0-9]*$'
            WHEN 'OPEN_LIBRARY_WORK_ID' THEN identifier_value ~ '^OL[1-9][0-9]*W$'
            WHEN 'OPEN_LIBRARY_EDITION_ID' THEN identifier_value ~ '^OL[1-9][0-9]*M$'
            WHEN 'OCLC' THEN identifier_value ~ '^[1-9][0-9]*$'
            WHEN 'WIKIDATA_ID' THEN identifier_value ~ '^Q[1-9][0-9]*$'
            WHEN 'DOI' THEN identifier_value ~ '^10\.[0-9]{4,9}/[^[:space:]]+$' AND identifier_value = lower(identifier_value)
            WHEN 'INTERNET_ARCHIVE_ID' THEN identifier_value !~ '[[:space:]/]'
            ELSE identifier_value !~ '[[:space:]]'
        END)
);

-- Source-first uniqueness does not cover cross-source lookup by type/value.
CREATE INDEX external_identifier_lookup_idx ON external_identifier(identifier_type, identifier_value);
CREATE INDEX external_identifier_book_idx ON external_identifier(book_id) WHERE book_id IS NOT NULL;
CREATE INDEX external_identifier_edition_idx ON external_identifier(edition_id) WHERE edition_id IS NOT NULL;
CREATE INDEX external_identifier_author_idx ON external_identifier(author_id) WHERE author_id IS NOT NULL;

CREATE TRIGGER source_updated_at BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER license_updated_at BEFORE UPDATE ON license
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER external_identifier_updated_at BEFORE UPDATE ON external_identifier
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO source(id, code, name, base_url, source_type) VALUES
    ('10000000-0000-4000-8000-000000000001', 'GUTENBERG', 'Project Gutenberg', 'https://www.gutenberg.org', 'DIGITAL_LIBRARY'),
    ('10000000-0000-4000-8000-000000000002', 'OPEN_LIBRARY', 'Open Library', 'https://openlibrary.org', 'EXTERNAL_CATALOG'),
    ('10000000-0000-4000-8000-000000000003', 'INTERNET_ARCHIVE', 'Internet Archive', 'https://archive.org', 'DIGITAL_LIBRARY'),
    ('10000000-0000-4000-8000-000000000004', 'ADMIN_UPLOAD', 'Administrative upload', NULL, 'ADMIN_UPLOAD'),
    ('10000000-0000-4000-8000-000000000005', 'INTERNAL_PIPELINE', 'Internal pipeline', NULL, 'INTERNAL_PIPELINE');

-- Reference vocabulary only: no blanket legal assertions from an unqualified label.
INSERT INTO license(id, code, name) VALUES
    ('20000000-0000-4000-8000-000000000001', 'PUBLIC_DOMAIN', 'Public domain (context requires review)'),
    ('20000000-0000-4000-8000-000000000002', 'CC0', 'CC0 (version and evidence require review)'),
    ('20000000-0000-4000-8000-000000000003', 'CC_BY', 'Creative Commons BY (version requires review)'),
    ('20000000-0000-4000-8000-000000000004', 'CC_BY_SA', 'Creative Commons BY-SA (version requires review)'),
    ('20000000-0000-4000-8000-000000000005', 'CC_BY_NC', 'Creative Commons BY-NC (version requires review)'),
    ('20000000-0000-4000-8000-000000000006', 'UNKNOWN', 'Unknown license');
