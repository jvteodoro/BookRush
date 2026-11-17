CREATE TABLE reporting.library_state_snapshot (
  snapshot_key VARCHAR(16) PRIMARY KEY CHECK (snapshot_key = 'CURRENT'),
  saved_book_pairs BIGINT NOT NULL,
  saved_books BIGINT NOT NULL,
  saved_subject_keys BIGINT NOT NULL,
  calculation_version VARCHAR(120) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL,
  source_watermark TIMESTAMPTZ
);
