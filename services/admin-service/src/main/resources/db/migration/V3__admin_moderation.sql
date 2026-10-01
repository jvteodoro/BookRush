CREATE TABLE IF NOT EXISTS admin.moderation (id UUID PRIMARY KEY, target TEXT NOT NULL, decision TEXT NOT NULL, actor_subject TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
