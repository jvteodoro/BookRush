CREATE TABLE reporting.content_coverage_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    language VARCHAR(16) NOT NULL,
    rights_status VARCHAR(32) NOT NULL,
    active_books BIGINT NOT NULL,
    books_with_active_assets BIGINT NOT NULL,
    available_asset_books BIGINT NOT NULL,
    reader_ready_books BIGINT NOT NULL,
    analyzed_books BIGINT NOT NULL,
    eligible_excerpt_books BIGINT NOT NULL,
    ranked_books BIGINT NOT NULL,
    eligible_excerpts BIGINT NOT NULL,
    ranked_excerpts BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (collected_at, language, rights_status)
);

CREATE INDEX reporting_content_coverage_time_idx
  ON reporting.content_coverage_snapshot(collected_at DESC, language, rights_status);
