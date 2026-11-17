CREATE TABLE IF NOT EXISTS reporting.reading_progress_daily (
    day DATE PRIMARY KEY,
    reader_book_pairs_10 BIGINT NOT NULL,
    reader_book_pairs_50 BIGINT NOT NULL,
    reader_book_pairs_100 BIGINT NOT NULL,
    save_actions BIGINT NOT NULL,
    unsave_actions BIGINT NOT NULL,
    unique_savers BIGINT NOT NULL,
    unique_unsavers BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_reading_progress_day_idx
  ON reporting.reading_progress_daily(day DESC);
