CREATE TABLE IF NOT EXISTS reporting.editorial_submission_funnel_daily (
    cohort_day DATE PRIMARY KEY,
    submissions BIGINT NOT NULL,
    reviewed_submissions BIGINT NOT NULL,
    approved_submissions BIGINT NOT NULL,
    published_submissions BIGINT NOT NULL,
    active_published_submissions BIGINT NOT NULL,
    rights_attested_submissions BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_editorial_funnel_cohort_day_idx
  ON reporting.editorial_submission_funnel_daily(cohort_day DESC);
