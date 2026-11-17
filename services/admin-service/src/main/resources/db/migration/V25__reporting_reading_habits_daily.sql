CREATE TABLE IF NOT EXISTS reporting.reading_habits_daily (
    day DATE PRIMARY KEY,
    active_readers BIGINT NOT NULL,
    reading_minutes BIGINT NOT NULL,
    completed_books BIGINT NOT NULL,
    streak_1_day_users BIGINT NOT NULL,
    streak_2_6_day_users BIGINT NOT NULL,
    streak_7_29_day_users BIGINT NOT NULL,
    streak_30_plus_day_users BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_reading_habits_day_idx
  ON reporting.reading_habits_daily(day DESC);
