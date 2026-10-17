ALTER TABLE analytics.annotation_dataset_version
  ADD COLUMN IF NOT EXISTS sampling_config_hash CHAR(64),
  ADD COLUMN IF NOT EXISTS lineage_sha256 CHAR(64);

ALTER TABLE analytics.annotation_dataset_version
  ADD CONSTRAINT annotation_dataset_sampling_hash_check
    CHECK (sampling_config_hash IS NULL OR sampling_config_hash ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT annotation_dataset_lineage_hash_check
    CHECK (lineage_sha256 IS NULL OR lineage_sha256 ~ '^[0-9a-f]{64}$');
