CREATE TABLE IF NOT EXISTS reader_profile.profile (subject_key TEXT PRIMARY KEY, display_name TEXT, bio TEXT, is_public BOOLEAN NOT NULL DEFAULT true, updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
