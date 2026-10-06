CREATE TABLE IF NOT EXISTS publisher.submission_metadata (
  submission_id UUID PRIMARY KEY REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  synopsis TEXT,
  language VARCHAR(3) CHECK (language IS NULL OR language ~ '^[a-z]{2,3}$'),
  isbn VARCHAR(17),
  publisher_name TEXT,
  keywords JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(keywords) = 'array'),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS publisher.submission_contributor (
  id UUID PRIMARY KEY,
  submission_id UUID NOT NULL REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  display_name TEXT NOT NULL CHECK (btrim(display_name) <> ''),
  role VARCHAR(24) NOT NULL
    CHECK (role IN ('AUTHOR', 'EDITOR', 'TRANSLATOR', 'ILLUSTRATOR', 'CONTRIBUTOR')),
  position INTEGER NOT NULL CHECK (position > 0),
  UNIQUE (submission_id, position)
);

CREATE TABLE IF NOT EXISTS publisher.rights_attestation (
  id UUID PRIMARY KEY,
  submission_id UUID NOT NULL REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  right_type VARCHAR(24) NOT NULL CHECK (right_type IN ('DISTRIBUTION', 'PROCESSING', 'DISPLAY')),
  territory VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' CHECK (btrim(territory) <> ''),
  evidence_reference TEXT,
  affirmed_by_subject TEXT NOT NULL,
  affirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (submission_id, right_type, territory)
);

CREATE TABLE IF NOT EXISTS publisher.publication_review (
  id UUID PRIMARY KEY,
  submission_id UUID NOT NULL REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED')),
  reviewer_subject_key TEXT NOT NULL,
  reason_code VARCHAR(80),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS publisher.published_work (
  id UUID PRIMARY KEY,
  submission_id UUID NOT NULL UNIQUE REFERENCES publisher.submission(id) ON DELETE RESTRICT,
  book_id UUID NOT NULL,
  edition_id UUID,
  publisher_subject_key TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED'
    CHECK (status IN ('PUBLISHED', 'SUSPENDED', 'WITHDRAWN')),
  published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  withdrawn_at TIMESTAMPTZ,
  CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS publisher.daily_publication_metric (
  day DATE NOT NULL,
  published_work_id UUID NOT NULL REFERENCES publisher.published_work(id) ON DELETE RESTRICT,
  impressions BIGINT NOT NULL DEFAULT 0 CHECK (impressions >= 0),
  opens BIGINT NOT NULL DEFAULT 0 CHECK (opens >= 0),
  reads BIGINT NOT NULL DEFAULT 0 CHECK (reads >= 0),
  likes BIGINT NOT NULL DEFAULT 0 CHECK (likes >= 0),
  shares BIGINT NOT NULL DEFAULT 0 CHECK (shares >= 0),
  comments BIGINT NOT NULL DEFAULT 0 CHECK (comments >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (day, published_work_id)
);

CREATE INDEX IF NOT EXISTS published_work_owner_idx
  ON publisher.published_work(publisher_subject_key, published_at DESC);
CREATE INDEX IF NOT EXISTS publication_review_submission_idx
  ON publisher.publication_review(submission_id, created_at DESC);
