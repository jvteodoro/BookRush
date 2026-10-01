CREATE TABLE IF NOT EXISTS recommendation.request (id UUID PRIMARY KEY, subject_key TEXT, model_version TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS recommendation.impression (id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES recommendation.request(id), book_id UUID NOT NULL, rank INTEGER NOT NULL, shown_at TIMESTAMPTZ NOT NULL DEFAULT now(), viewable BOOLEAN NOT NULL DEFAULT false);
CREATE INDEX IF NOT EXISTS recommendation_impression_request_idx ON recommendation.impression(request_id);
