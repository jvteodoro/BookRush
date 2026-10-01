CREATE TABLE IF NOT EXISTS social.shares (
  id UUID PRIMARY KEY,
  subject_key TEXT NOT NULL,
  book_id UUID NOT NULL,
  channel TEXT NOT NULL CHECK (length(channel) <= 40),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS social_shares_book_idx ON social.shares(book_id, created_at DESC);
