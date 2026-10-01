CREATE TABLE IF NOT EXISTS behavior.rejects (id UUID PRIMARY KEY, event_key TEXT, reason TEXT NOT NULL, payload JSONB NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS behavior.schema_registry (event_type TEXT PRIMARY KEY, schema_version TEXT NOT NULL, schema_hash TEXT NOT NULL, active BOOLEAN NOT NULL DEFAULT true);
