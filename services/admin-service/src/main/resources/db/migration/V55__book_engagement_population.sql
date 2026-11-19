ALTER TABLE reporting.book_engagement_daily
  ADD COLUMN IF NOT EXISTS population VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE reporting.book_engagement_daily DROP CONSTRAINT IF EXISTS book_engagement_daily_pkey;
ALTER TABLE reporting.book_engagement_daily ADD CONSTRAINT book_engagement_daily_pkey
  PRIMARY KEY (day, aggregation_level, aggregation_key, population);
CREATE INDEX IF NOT EXISTS reporting_book_engagement_population_idx
  ON reporting.book_engagement_daily(population, day DESC, unique_readers DESC);
