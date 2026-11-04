CREATE TABLE IF NOT EXISTS reporting.social_daily (
  day DATE PRIMARY KEY,
  follow_count BIGINT NOT NULL DEFAULT 0 CHECK (follow_count >= 0),
  like_count BIGINT NOT NULL DEFAULT 0 CHECK (like_count >= 0),
  comment_count BIGINT NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
  share_count BIGINT NOT NULL DEFAULT 0 CHECK (share_count >= 0),
  book_opened_count BIGINT NOT NULL DEFAULT 0 CHECK (book_opened_count >= 0),
  book_saved_count BIGINT NOT NULL DEFAULT 0 CHECK (book_saved_count >= 0),
  unique_actors BIGINT NOT NULL DEFAULT 0 CHECK (unique_actors >= 0),
  reports_open BIGINT NOT NULL DEFAULT 0 CHECK (reports_open >= 0),
  moderation_backlog BIGINT NOT NULL DEFAULT 0 CHECK (moderation_backlog >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS reporting.publisher_daily (
  day DATE PRIMARY KEY,
  published_work_count BIGINT NOT NULL DEFAULT 0 CHECK (published_work_count >= 0),
  impressions BIGINT NOT NULL DEFAULT 0 CHECK (impressions >= 0),
  opens BIGINT NOT NULL DEFAULT 0 CHECK (opens >= 0),
  reads BIGINT NOT NULL DEFAULT 0 CHECK (reads >= 0),
  likes BIGINT NOT NULL DEFAULT 0 CHECK (likes >= 0),
  shares BIGINT NOT NULL DEFAULT 0 CHECK (shares >= 0),
  comments BIGINT NOT NULL DEFAULT 0 CHECK (comments >= 0),
  calculation_version VARCHAR(80) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS reporting_social_daily_day_idx ON reporting.social_daily(day DESC);
CREATE INDEX IF NOT EXISTS reporting_publisher_daily_day_idx ON reporting.publisher_daily(day DESC);
