ALTER TABLE social.comments ADD COLUMN parent_comment_id UUID;
ALTER TABLE social.comments ADD CONSTRAINT comments_id_book_unique UNIQUE (id, book_id);
ALTER TABLE social.comments ADD CONSTRAINT comments_parent_same_book_fk
  FOREIGN KEY (parent_comment_id, book_id) REFERENCES social.comments(id, book_id) ON DELETE RESTRICT;
ALTER TABLE social.comments ADD CONSTRAINT comments_not_own_parent CHECK (parent_comment_id IS NULL OR parent_comment_id <> id);
CREATE INDEX social_comments_book_parent_idx ON social.comments(book_id, parent_comment_id, created_at, id);
