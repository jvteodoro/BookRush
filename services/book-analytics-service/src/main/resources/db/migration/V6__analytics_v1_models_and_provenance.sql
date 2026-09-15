-- Content Analytics V1 additive model/artifact lineage. Previous migrations remain immutable.
ALTER TABLE analytics.analysis_run ADD COLUMN configuration_hash CHAR(64) CHECK (configuration_hash IS NULL OR configuration_hash ~ '^[0-9a-f]{64}$');
ALTER TABLE analytics.analysis_run ADD COLUMN code_version VARCHAR(120);
ALTER TABLE analytics.analysis_run ADD COLUMN stage VARCHAR(40) NOT NULL DEFAULT 'STRUCTURAL';
ALTER TABLE analytics.analysis_job ADD COLUMN current_stage VARCHAR(40) NOT NULL DEFAULT 'VALIDATE_LANGUAGE';
ALTER TABLE analytics.analysis_job_item ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE TABLE analytics.model_artifact (
  id UUID PRIMARY KEY,
  model_id VARCHAR(160) NOT NULL UNIQUE,
  framework VARCHAR(80) NOT NULL,
  artifact_uri TEXT,
  revision VARCHAR(255),
  sha256 CHAR(64) CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
  license VARCHAR(160),
  status VARCHAR(32) NOT NULL CHECK (status IN ('AVAILABLE','MISSING','INVALID')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE analytics.document_feature ADD COLUMN value_status VARCHAR(32) NOT NULL DEFAULT 'VALID' CHECK (value_status IN ('VALID','UNSUPPORTED','INVALID_INPUT','INSUFFICIENT_SAMPLE','MODEL_UNAVAILABLE','ERROR'));
ALTER TABLE analytics.document_feature ADD COLUMN sample_size INTEGER;
ALTER TABLE analytics.document_feature ADD COLUMN warning TEXT;
ALTER TABLE analytics.chapter_feature ADD COLUMN value_status VARCHAR(32) NOT NULL DEFAULT 'VALID' CHECK (value_status IN ('VALID','UNSUPPORTED','INVALID_INPUT','INSUFFICIENT_SAMPLE','MODEL_UNAVAILABLE','ERROR'));
ALTER TABLE analytics.chapter_feature ADD COLUMN sample_size INTEGER;
ALTER TABLE analytics.chapter_feature ADD COLUMN warning TEXT;
ALTER TABLE analytics.excerpt_feature ADD COLUMN value_status VARCHAR(32) NOT NULL DEFAULT 'VALID' CHECK (value_status IN ('VALID','UNSUPPORTED','INVALID_INPUT','INSUFFICIENT_SAMPLE','MODEL_UNAVAILABLE','ERROR'));
ALTER TABLE analytics.excerpt_feature ADD COLUMN sample_size INTEGER;
ALTER TABLE analytics.excerpt_feature ADD COLUMN warning TEXT;

CREATE TABLE analytics.chapter_embedding (
  id UUID PRIMARY KEY,
  chapter_id UUID NOT NULL REFERENCES catalog.book_chapter(id) ON DELETE RESTRICT,
  embedding_model_id UUID NOT NULL REFERENCES analytics.embedding_model(id) ON DELETE RESTRICT,
  input_sha256 CHAR(64) NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
  dimension INTEGER NOT NULL CHECK (dimension > 0),
  pooling_strategy VARCHAR(80) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  bucket VARCHAR(255), object_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (chapter_id, embedding_model_id, input_sha256),
  CHECK ((bucket IS NULL) = (object_key IS NULL))
);
CREATE INDEX chapter_embedding_model_idx ON analytics.chapter_embedding(embedding_model_id, chapter_id);

CREATE TABLE analytics.corpus_frequency_model (
  id UUID PRIMARY KEY,
  language VARCHAR(8) NOT NULL,
  corpus_snapshot VARCHAR(255) NOT NULL,
  tokenizer_version VARCHAR(120) NOT NULL,
  vocabulary_size BIGINT NOT NULL CHECK (vocabulary_size >= 0),
  token_count BIGINT NOT NULL CHECK (token_count >= 0),
  smoothing_alpha DOUBLE PRECISION NOT NULL CHECK (smoothing_alpha > 0),
  artifact_uri TEXT NOT NULL,
  artifact_sha256 CHAR(64) NOT NULL CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(language, corpus_snapshot, tokenizer_version)
);

CREATE TABLE analytics.style_normalization_model (
  id UUID PRIMARY KEY,
  language VARCHAR(8) NOT NULL,
  corpus_snapshot VARCHAR(255) NOT NULL,
  feature_order JSONB NOT NULL,
  means JSONB NOT NULL,
  standard_deviations JSONB NOT NULL,
  artifact_sha256 CHAR(64) NOT NULL CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(language, corpus_snapshot)
);

CREATE TABLE analytics.prototype_set (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL,
  version VARCHAR(80) NOT NULL,
  embedding_model_id UUID NOT NULL REFERENCES analytics.embedding_model(id) ON DELETE RESTRICT,
  source_config_sha256 CHAR(64) NOT NULL CHECK (source_config_sha256 ~ '^[0-9a-f]{64}$'),
  artifact_uri TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(code, version)
);

CREATE TABLE analytics.topic_model (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL,
  version VARCHAR(80) NOT NULL,
  taxonomy_version VARCHAR(120) NOT NULL,
  dataset_manifest_uri TEXT NOT NULL,
  artifact_uri TEXT NOT NULL,
  metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(32) NOT NULL CHECK (status IN ('EXPERIMENTAL','ACCEPTED','RETIRED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(code, version)
);

CREATE TABLE analytics.excerpt_rank (
  id UUID PRIMARY KEY,
  excerpt_id UUID NOT NULL REFERENCES analytics.excerpt(id) ON DELETE RESTRICT,
  ranker_code VARCHAR(120) NOT NULL,
  ranker_version VARCHAR(80) NOT NULL,
  configuration_hash CHAR(64) NOT NULL CHECK (configuration_hash ~ '^[0-9a-f]{64}$'),
  components JSONB NOT NULL,
  weights JSONB NOT NULL,
  final_score DOUBLE PRECISION NOT NULL,
  exclusion_reason VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(excerpt_id, ranker_code, ranker_version, configuration_hash)
);
