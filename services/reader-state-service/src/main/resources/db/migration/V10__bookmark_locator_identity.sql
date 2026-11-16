-- Legacy clients still deduplicate by codepoint. Readium locations must not
-- collide merely because they share a rounded percentage (or the default zero).
DROP INDEX reader_state.uq_reader_bookmark;
CREATE UNIQUE INDEX uq_reader_bookmark ON reader_state.bookmarks
  (subject_key, book_id, position_codepoint) WHERE locator IS NULL;
CREATE UNIQUE INDEX uq_reader_bookmark_locator ON reader_state.bookmarks
  (subject_key, book_id, md5(locator::text)) WHERE locator IS NOT NULL;
