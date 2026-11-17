ALTER TABLE reporting.reader_day
  ADD COLUMN IF NOT EXISTS weekly_active_users BIGINT,
  ADD COLUMN IF NOT EXISTS monthly_active_users BIGINT,
  ADD COLUMN IF NOT EXISTS returning_users BIGINT;
