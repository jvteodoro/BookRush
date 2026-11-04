CREATE TABLE IF NOT EXISTS reporting.excerpt_performance_daily (
    day DATE NOT NULL,
    excerpt_id UUID NOT NULL,
    book_id UUID NOT NULL,
    book_title TEXT,
    language VARCHAR(32),
    model_version VARCHAR(160) NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    eligible BOOLEAN NOT NULL,
    impressions BIGINT NOT NULL,
    viewable_count BIGINT NOT NULL,
    attributed_open_count BIGINT NOT NULL,
    attributed_read_count BIGINT NOT NULL,
    attributed_complete_count BIGINT NOT NULL,
    unique_subjects BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, excerpt_id, model_version, strategy)
);

CREATE INDEX IF NOT EXISTS reporting_excerpt_performance_day_idx
  ON reporting.excerpt_performance_daily(day DESC, eligible, impressions DESC);
CREATE INDEX IF NOT EXISTS reporting_excerpt_performance_excerpt_idx
  ON reporting.excerpt_performance_daily(excerpt_id, day DESC);
