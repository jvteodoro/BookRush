CREATE INDEX book_canonical_title_lower_idx ON catalog.book (lower(canonical_title));
CREATE INDEX book_original_title_lower_idx ON catalog.book (lower(original_title));
CREATE INDEX author_name_lower_idx ON catalog.author (lower(name));
CREATE INDEX subject_name_lower_idx ON catalog.subject (lower(canonical_name));
CREATE INDEX book_description_search_idx ON catalog.book USING gin (to_tsvector('simple', coalesce(description,'')));
