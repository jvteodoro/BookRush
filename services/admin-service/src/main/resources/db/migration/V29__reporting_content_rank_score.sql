CREATE TABLE reporting.content_rank_score_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    language VARCHAR(16) NOT NULL,
    rights_status VARCHAR(32) NOT NULL,
    ranker_code VARCHAR(120) NOT NULL,
    ranker_version VARCHAR(80) NOT NULL,
    configuration_hash CHAR(64) NOT NULL,
    score_decile SMALLINT NOT NULL CHECK (score_decile BETWEEN 0 AND 9),
    score_min DOUBLE PRECISION NOT NULL,
    score_max DOUBLE PRECISION NOT NULL,
    excerpt_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (collected_at, language, rights_status, ranker_code,
      ranker_version, configuration_hash, score_decile)
);

CREATE INDEX reporting_content_rank_score_time_idx
  ON reporting.content_rank_score_snapshot(collected_at DESC, language, ranker_code, ranker_version);
