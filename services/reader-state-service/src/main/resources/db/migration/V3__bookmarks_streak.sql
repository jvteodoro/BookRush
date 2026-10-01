CREATE UNIQUE INDEX IF NOT EXISTS uq_reader_bookmark ON reader_state.bookmarks(subject_key,book_id,position_codepoint);
CREATE TABLE IF NOT EXISTS reader_state.reading_day(subject_key TEXT NOT NULL, day DATE NOT NULL, minutes INTEGER NOT NULL DEFAULT 0, books_completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(subject_key,day));
