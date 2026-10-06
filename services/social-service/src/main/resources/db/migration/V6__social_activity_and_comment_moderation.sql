CREATE TABLE IF NOT EXISTS social.comment_moderation_state (
  comment_id UUID PRIMARY KEY REFERENCES social.comments(id) ON DELETE RESTRICT,
  visibility VARCHAR(16) NOT NULL DEFAULT 'VISIBLE'
    CHECK (visibility IN ('VISIBLE', 'HIDDEN', 'REMOVED')),
  reason_code VARCHAR(80),
  moderation_case_id UUID,
  updated_by_subject TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS social.activity (
  id UUID PRIMARY KEY,
  actor_subject_key TEXT NOT NULL,
  activity_type VARCHAR(24) NOT NULL
    CHECK (activity_type IN ('FOLLOW', 'LIKE', 'COMMENT', 'SHARE', 'BOOK_OPENED', 'BOOK_SAVED')),
  book_id UUID,
  comment_id UUID REFERENCES social.comments(id) ON DELETE RESTRICT,
  target_subject_key TEXT,
  visibility_snapshot VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
    CHECK (visibility_snapshot IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (num_nonnulls(book_id, comment_id, target_subject_key) >= 1)
);

CREATE INDEX IF NOT EXISTS social_activity_actor_time_idx
  ON social.activity(actor_subject_key, occurred_at DESC);
CREATE INDEX IF NOT EXISTS social_activity_book_time_idx
  ON social.activity(book_id, occurred_at DESC)
  WHERE book_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS social_comment_moderation_visibility_idx
  ON social.comment_moderation_state(visibility)
  WHERE visibility <> 'VISIBLE';
