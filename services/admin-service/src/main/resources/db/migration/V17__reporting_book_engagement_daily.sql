CREATE TABLE IF NOT EXISTS reporting.book_engagement_daily (
    day DATE NOT NULL,
    aggregation_level VARCHAR(12) NOT NULL CHECK (aggregation_level IN ('BOOK', 'LANGUAGE')),
    aggregation_key VARCHAR(120) NOT NULL,
    book_id UUID,
    book_title TEXT,
    language VARCHAR(3) NOT NULL,
    impressions BIGINT NOT NULL,
    viewable_count BIGINT NOT NULL,
    opens BIGINT NOT NULL,
    reads BIGINT NOT NULL,
    active_minutes BIGINT NOT NULL,
    unique_readers BIGINT NOT NULL,
    completions BIGINT NOT NULL,
    interactions BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, aggregation_level, aggregation_key)
);

CREATE INDEX IF NOT EXISTS reporting_book_engagement_day_idx
  ON reporting.book_engagement_daily(day DESC, aggregation_level, unique_readers DESC);
