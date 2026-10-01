CREATE TABLE IF NOT EXISTS social.likes (subject_key TEXT NOT NULL, book_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(subject_key,book_id));
CREATE TABLE IF NOT EXISTS social.follows (follower TEXT NOT NULL, followed TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(follower,followed));
CREATE TABLE IF NOT EXISTS social.comments (id UUID PRIMARY KEY, subject_key TEXT NOT NULL, book_id UUID NOT NULL, body TEXT NOT NULL CHECK(length(body)<=2000), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
