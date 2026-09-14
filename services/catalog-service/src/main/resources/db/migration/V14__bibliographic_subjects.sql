CREATE TABLE subject (
  id UUID PRIMARY KEY,
  canonical_name TEXT NOT NULL CHECK (btrim(canonical_name) <> ''),
  normalized_name TEXT NOT NULL CHECK (btrim(normalized_name) <> ''),
  scheme VARCHAR(32) NOT NULL CHECK (scheme ~ '^[A-Z][A-Z0-9_]*$'),
  parent_id UUID REFERENCES subject(id) ON DELETE RESTRICT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (scheme, normalized_name)
);

CREATE TABLE book_subject (
  book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
  subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE RESTRICT,
  source_id UUID NOT NULL REFERENCES source(id) ON DELETE RESTRICT,
  source_record_id UUID REFERENCES source_record(id) ON DELETE RESTRICT,
  confidence NUMERIC(5,4) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  assignment_method VARCHAR(32) NOT NULL CHECK (assignment_method IN ('SOURCE','MATCHED','CURATED','MODEL')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (book_id, subject_id, source_id),
  UNIQUE (book_id, subject_id, source_record_id)
);
CREATE INDEX subject_parent_idx ON subject(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX book_subject_subject_idx ON book_subject(subject_id, book_id);
CREATE INDEX book_subject_source_idx ON book_subject(source_id, created_at DESC);
