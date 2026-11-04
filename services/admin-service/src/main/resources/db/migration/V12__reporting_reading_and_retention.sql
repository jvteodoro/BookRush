CREATE TABLE IF NOT EXISTS reporting.reading_daily (
    day DATE NOT NULL,
    unique_readers BIGINT NOT NULL,
    sessions BIGINT NOT NULL,
    active_seconds BIGINT NOT NULL,
    p50_session_seconds DOUBLE PRECISION,
    p90_session_seconds DOUBLE PRECISION,
    books_opened BIGINT NOT NULL,
    books_started BIGINT NOT NULL,
    completions BIGINT NOT NULL,
    saved_books BIGINT NOT NULL,
    unique_savers BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day)
);

CREATE INDEX IF NOT EXISTS reporting_reading_daily_day_idx
  ON reporting.reading_daily(day DESC);

CREATE TABLE IF NOT EXISTS reporting.retention_cohort (
    cohort_day DATE NOT NULL,
    offset_day INTEGER NOT NULL CHECK (offset_day IN (0, 1, 7, 30)),
    cohort_users BIGINT NOT NULL,
    retained_users BIGINT NOT NULL,
    mature BOOLEAN NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (cohort_day, offset_day)
);

CREATE INDEX IF NOT EXISTS reporting_retention_cohort_day_idx
  ON reporting.retention_cohort(cohort_day DESC, offset_day);
