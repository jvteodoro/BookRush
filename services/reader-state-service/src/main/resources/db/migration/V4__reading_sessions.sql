CREATE TABLE IF NOT EXISTS reader_state.recent (subject_key TEXT NOT NULL, book_id UUID NOT NULL, last_opened_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(subject_key,book_id));
CREATE TABLE IF NOT EXISTS reader_state.session (id UUID PRIMARY KEY, subject_key TEXT NOT NULL, started_at TIMESTAMPTZ NOT NULL, ended_at TIMESTAMPTZ, active_seconds INTEGER NOT NULL DEFAULT 0);
