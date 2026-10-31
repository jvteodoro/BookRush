-- Author removal is independent of moderation and cannot be undone by restore.
-- Retain the original row for report references and administrative evidence.
ALTER TABLE social.comments
  ADD COLUMN author_deleted_at TIMESTAMPTZ;

CREATE INDEX social_comments_available_book_idx
  ON social.comments(book_id, created_at, id)
  WHERE author_deleted_at IS NULL;
