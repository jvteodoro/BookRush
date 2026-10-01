ALTER TABLE publisher.submission ADD COLUMN IF NOT EXISTS catalog_book_id UUID;
CREATE INDEX IF NOT EXISTS submission_catalog_book_idx ON publisher.submission(subject_key, catalog_book_id);
