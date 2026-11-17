ALTER TABLE reporting.reading_progress_daily
    ADD COLUMN IF NOT EXISTS reader_book_pairs_started BIGINT NOT NULL DEFAULT 0;
