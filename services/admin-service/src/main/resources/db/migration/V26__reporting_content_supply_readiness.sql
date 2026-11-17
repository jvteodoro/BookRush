ALTER TABLE reporting.content_supply_snapshot
  ADD COLUMN reader_ready_books BIGINT,
  ADD COLUMN excerpt_ready_books BIGINT;
