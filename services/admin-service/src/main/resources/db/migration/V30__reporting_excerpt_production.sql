CREATE TABLE reporting.excerpt_production_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    language VARCHAR(16) NOT NULL,
    rights_status VARCHAR(32) NOT NULL,
    generation_method VARCHAR(80) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    reason_bucket VARCHAR(48) NOT NULL,
    excerpt_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (collected_at, language, rights_status, generation_method,
      outcome, reason_bucket)
);

CREATE INDEX reporting_excerpt_production_time_idx
  ON reporting.excerpt_production_snapshot(collected_at DESC, language, outcome);
