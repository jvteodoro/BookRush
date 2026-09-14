ALTER TABLE analytics.analysis_job ADD COLUMN request_hash VARCHAR(64) NOT NULL DEFAULT repeat('0', 64);
ALTER TABLE analytics.analysis_job ALTER COLUMN request_hash DROP DEFAULT;
CREATE INDEX analysis_job_request_hash_idx ON analytics.analysis_job(operation_key, request_hash);
