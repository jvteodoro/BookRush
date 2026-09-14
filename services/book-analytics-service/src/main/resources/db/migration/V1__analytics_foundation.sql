CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.analyzer (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  analyzer_type VARCHAR(32) NOT NULL CHECK (analyzer_type IN ('DETERMINISTIC','STATISTICAL','EMBEDDING','CLASSIFIER','LOCAL_LLM','REMOTE_LLM','BEHAVIORAL')),
  implementation VARCHAR(255) NOT NULL,
  model_name VARCHAR(255),
  model_version VARCHAR(120),
  code_version VARCHAR(120) NOT NULL,
  configuration_hash CHAR(64) CHECK (configuration_hash IS NULL OR configuration_hash ~ '^[0-9a-f]{64}$'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analytics.analysis_run (
  id UUID PRIMARY KEY,
  analyzer_id UUID NOT NULL REFERENCES analytics.analyzer(id) ON DELETE RESTRICT,
  input_asset_version_id UUID NOT NULL REFERENCES catalog.book_asset_version(id) ON DELETE RESTRICT,
  status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','CANCELLED')),
  configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  error_code VARCHAR(80),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
);
CREATE INDEX analysis_run_input_idx ON analytics.analysis_run(input_asset_version_id, created_at DESC);
CREATE INDEX analysis_run_status_idx ON analytics.analysis_run(status, created_at);

CREATE TABLE analytics.feature_definition (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  scope VARCHAR(16) NOT NULL CHECK (scope IN ('DOCUMENT','CHAPTER','EXCERPT')),
  value_type VARCHAR(16) NOT NULL CHECK (value_type IN ('NUMBER','BOOLEAN','TEXT','JSON')),
  unit VARCHAR(80),
  min_value DOUBLE PRECISION,
  max_value DOUBLE PRECISION,
  version INTEGER NOT NULL CHECK (version > 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);

CREATE TABLE analytics.excerpt (
  id UUID PRIMARY KEY,
  source_asset_version_id UUID NOT NULL REFERENCES catalog.book_asset_version(id) ON DELETE RESTRICT,
  chapter_id UUID REFERENCES catalog.book_chapter(id) ON DELETE RESTRICT,
  start_codepoint INTEGER NOT NULL CHECK (start_codepoint >= 0),
  end_codepoint INTEGER NOT NULL CHECK (end_codepoint > start_codepoint),
  text TEXT NOT NULL CHECK (btrim(text) <> ''),
  text_sha256 CHAR(64) NOT NULL CHECK (text_sha256 ~ '^[0-9a-f]{64}$'),
  word_count INTEGER NOT NULL CHECK (word_count >= 0),
  sentence_count INTEGER NOT NULL CHECK (sentence_count >= 0),
  generation_method VARCHAR(80) NOT NULL,
  generator_version VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (source_asset_version_id, start_codepoint, end_codepoint, generator_version)
);
CREATE INDEX excerpt_source_idx ON analytics.excerpt(source_asset_version_id, start_codepoint);
CREATE INDEX excerpt_chapter_idx ON analytics.excerpt(chapter_id, start_codepoint);

CREATE TABLE analytics.document_feature (
  analysis_run_id UUID NOT NULL REFERENCES analytics.analysis_run(id) ON DELETE RESTRICT,
  feature_definition_id UUID NOT NULL REFERENCES analytics.feature_definition(id) ON DELETE RESTRICT,
  input_asset_version_id UUID NOT NULL REFERENCES catalog.book_asset_version(id) ON DELETE RESTRICT,
  numeric_value DOUBLE PRECISION,
  text_value TEXT,
  json_value JSONB,
  confidence DOUBLE PRECISION CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  computed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (analysis_run_id, feature_definition_id),
  CHECK (num_nonnulls(numeric_value, text_value, json_value) = 1)
);
CREATE INDEX document_feature_lookup_idx ON analytics.document_feature(feature_definition_id, input_asset_version_id);

CREATE TABLE analytics.chapter_feature (
  analysis_run_id UUID NOT NULL REFERENCES analytics.analysis_run(id) ON DELETE RESTRICT,
  feature_definition_id UUID NOT NULL REFERENCES analytics.feature_definition(id) ON DELETE RESTRICT,
  chapter_id UUID NOT NULL REFERENCES catalog.book_chapter(id) ON DELETE RESTRICT,
  numeric_value DOUBLE PRECISION,
  text_value TEXT,
  json_value JSONB,
  confidence DOUBLE PRECISION CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  computed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (analysis_run_id, feature_definition_id, chapter_id),
  CHECK (num_nonnulls(numeric_value, text_value, json_value) = 1)
);

CREATE TABLE analytics.excerpt_feature (
  analysis_run_id UUID NOT NULL REFERENCES analytics.analysis_run(id) ON DELETE RESTRICT,
  feature_definition_id UUID NOT NULL REFERENCES analytics.feature_definition(id) ON DELETE RESTRICT,
  excerpt_id UUID NOT NULL REFERENCES analytics.excerpt(id) ON DELETE RESTRICT,
  numeric_value DOUBLE PRECISION,
  text_value TEXT,
  json_value JSONB,
  confidence DOUBLE PRECISION CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  computed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (analysis_run_id, feature_definition_id, excerpt_id),
  CHECK (num_nonnulls(numeric_value, text_value, json_value) = 1)
);
CREATE INDEX excerpt_feature_lookup_idx ON analytics.excerpt_feature(feature_definition_id, excerpt_id);

CREATE TABLE analytics.embedding_model (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL UNIQUE,
  model_name VARCHAR(255) NOT NULL,
  model_version VARCHAR(120) NOT NULL,
  dimension INTEGER NOT NULL CHECK (dimension > 0),
  pooling_strategy VARCHAR(80),
  artifact_sha256 CHAR(64) CHECK (artifact_sha256 IS NULL OR artifact_sha256 ~ '^[0-9a-f]{64}$'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analytics.excerpt_embedding (
  id UUID PRIMARY KEY,
  excerpt_id UUID NOT NULL REFERENCES analytics.excerpt(id) ON DELETE RESTRICT,
  embedding_model_id UUID NOT NULL REFERENCES analytics.embedding_model(id) ON DELETE RESTRICT,
  input_sha256 CHAR(64) NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
  dimension INTEGER NOT NULL CHECK (dimension > 0),
  storage_provider VARCHAR(32) NOT NULL,
  bucket VARCHAR(255),
  object_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (excerpt_id, embedding_model_id, input_sha256),
  CHECK ((bucket IS NULL) = (object_key IS NULL))
);
CREATE INDEX excerpt_embedding_model_idx ON analytics.excerpt_embedding(embedding_model_id, excerpt_id);

CREATE TABLE analytics.document_embedding (
  id UUID PRIMARY KEY,
  input_asset_version_id UUID NOT NULL REFERENCES catalog.book_asset_version(id) ON DELETE RESTRICT,
  embedding_model_id UUID NOT NULL REFERENCES analytics.embedding_model(id) ON DELETE RESTRICT,
  input_sha256 CHAR(64) NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
  dimension INTEGER NOT NULL CHECK (dimension > 0),
  storage_provider VARCHAR(32) NOT NULL,
  bucket VARCHAR(255),
  object_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (input_asset_version_id, embedding_model_id, input_sha256),
  CHECK ((bucket IS NULL) = (object_key IS NULL))
);
