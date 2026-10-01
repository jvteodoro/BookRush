CREATE INDEX IF NOT EXISTS book_canonical_title_lower_idx ON book (lower(canonical_title));
CREATE INDEX IF NOT EXISTS book_original_title_lower_idx ON book (lower(original_title));
CREATE INDEX IF NOT EXISTS author_name_lower_idx ON author (lower(name));
CREATE INDEX IF NOT EXISTS subject_name_lower_idx ON subject (lower(canonical_name));
CREATE INDEX IF NOT EXISTS book_description_search_idx ON book USING gin (to_tsvector('simple', coalesce(description,'')));
