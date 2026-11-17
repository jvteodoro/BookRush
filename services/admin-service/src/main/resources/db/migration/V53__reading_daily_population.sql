ALTER TABLE reporting.reading_daily
  ADD COLUMN IF NOT EXISTS population VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE reporting.reading_daily DROP CONSTRAINT IF EXISTS reading_daily_pkey;
ALTER TABLE reporting.reading_daily ADD CONSTRAINT reading_daily_pkey PRIMARY KEY (day, population);
CREATE INDEX IF NOT EXISTS reporting_reading_daily_population_day_idx
  ON reporting.reading_daily(population, day DESC);
