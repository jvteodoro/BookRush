-- Search combines bibliographic text with author/subject fallbacks. Trigram
-- indexes keep the contains matches useful for names and short titles.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS book_search_tsvector_idx
    ON catalog.book USING gin (
        to_tsvector('simple', concat_ws(' ', canonical_title, original_title, description))
    );

CREATE INDEX IF NOT EXISTS book_canonical_title_trgm_idx
    ON catalog.book USING gin (canonical_title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS book_original_title_trgm_idx
    ON catalog.book USING gin (original_title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS author_name_trgm_idx
    ON catalog.author USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS subject_name_trgm_idx
    ON catalog.subject USING gin (canonical_name gin_trgm_ops);
