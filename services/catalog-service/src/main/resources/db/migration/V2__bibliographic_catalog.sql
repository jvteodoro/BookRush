CREATE TABLE book (
    id UUID PRIMARY KEY,
    canonical_title TEXT NOT NULL CHECK (btrim(canonical_title) <> ''),
    original_title TEXT,
    original_language VARCHAR(3) CHECK (original_language ~ '^[a-z]{2,3}$'),
    first_publication_year SMALLINT CHECK (first_publication_year BETWEEN -9999 AND 9999 AND first_publication_year <> 0),
    description TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'REVIEW_REQUIRED'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'MERGED', 'REVIEW_REQUIRED')),
    merged_into_id UUID REFERENCES book(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT book_merge_target CHECK (
        (status = 'MERGED' AND merged_into_id IS NOT NULL AND merged_into_id <> id)
        OR (status <> 'MERGED' AND merged_into_id IS NULL))
);

CREATE TABLE author (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    normalized_name TEXT CHECK (normalized_name IS NULL OR btrim(normalized_name) <> ''),
    birth_year SMALLINT CHECK (birth_year BETWEEN -9999 AND 9999 AND birth_year <> 0),
    death_year SMALLINT CHECK (death_year BETWEEN -9999 AND 9999 AND death_year <> 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT author_year_order CHECK (death_year >= birth_year)
);

CREATE TABLE edition (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
    title TEXT,
    subtitle TEXT,
    language VARCHAR(3) CHECK (language ~ '^[a-z]{2,3}$'),
    publisher TEXT,
    publication_year SMALLINT CHECK (publication_year BETWEEN -9999 AND 9999 AND publication_year <> 0),
    edition_name TEXT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT edition_id_book UNIQUE (id, book_id)
);

CREATE TABLE book_author (
    book_id UUID NOT NULL REFERENCES book(id) ON DELETE RESTRICT,
    author_id UUID NOT NULL REFERENCES author(id) ON DELETE RESTRICT,
    role VARCHAR(24) NOT NULL CHECK (role IN ('AUTHOR', 'EDITOR', 'TRANSLATOR', 'ILLUSTRATOR', 'CONTRIBUTOR')),
    position INTEGER NOT NULL CHECK (position > 0),
    PRIMARY KEY (book_id, author_id, role),
    CONSTRAINT book_author_credit_order UNIQUE (book_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE edition_author (
    edition_id UUID NOT NULL REFERENCES edition(id) ON DELETE RESTRICT,
    author_id UUID NOT NULL REFERENCES author(id) ON DELETE RESTRICT,
    role VARCHAR(24) NOT NULL CHECK (role IN ('AUTHOR', 'EDITOR', 'TRANSLATOR', 'ILLUSTRATOR', 'CONTRIBUTOR')),
    position INTEGER NOT NULL CHECK (position > 0),
    PRIMARY KEY (edition_id, author_id, role),
    CONSTRAINT edition_author_credit_order UNIQUE (edition_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

-- PKs already index the leftmost ownership keys; reverse credit lookup needs these.
CREATE INDEX book_canonical_title_idx ON book(canonical_title);
CREATE INDEX edition_book_idx ON edition(book_id);
CREATE INDEX book_author_author_idx ON book_author(author_id);
CREATE INDEX edition_author_author_idx ON edition_author(author_id);
CREATE INDEX book_merge_target_idx ON book(merged_into_id) WHERE merged_into_id IS NOT NULL;

CREATE TRIGGER book_updated_at BEFORE UPDATE ON book
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER author_updated_at BEFORE UPDATE ON author
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER edition_updated_at BEFORE UPDATE ON edition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
