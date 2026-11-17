ALTER TABLE reporting.retention_cohort
  ADD COLUMN IF NOT EXISTS population VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE reporting.retention_cohort DROP CONSTRAINT IF EXISTS retention_cohort_pkey;
ALTER TABLE reporting.retention_cohort ADD CONSTRAINT retention_cohort_pkey
  PRIMARY KEY (cohort_day, offset_day, population);
CREATE INDEX IF NOT EXISTS reporting_retention_cohort_population_idx
  ON reporting.retention_cohort(population, cohort_day DESC, offset_day);
