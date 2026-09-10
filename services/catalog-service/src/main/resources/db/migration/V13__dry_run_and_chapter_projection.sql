-- Dry-run items are terminal without creating catalog or storage side effects.
ALTER TABLE ingestion_item DROP CONSTRAINT IF EXISTS ingestion_item_status_check;
ALTER TABLE ingestion_item ADD CONSTRAINT ingestion_item_status_check
  CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','SKIPPED','FAILED','CANCELLED'));
