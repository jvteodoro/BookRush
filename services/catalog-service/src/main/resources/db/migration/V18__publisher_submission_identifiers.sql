-- Publisher uploads already use this canonical identifier, but V3 rejected it.
-- Preserve all existing identifiers and their target rules.
ALTER TABLE external_identifier DROP CONSTRAINT external_identifier_identifier_type_check;
ALTER TABLE external_identifier ADD CONSTRAINT external_identifier_identifier_type_check CHECK (identifier_type IN (
  'ISBN10','ISBN13','GUTENBERG_ID','OPEN_LIBRARY_WORK_ID','OPEN_LIBRARY_EDITION_ID',
  'OPEN_LIBRARY_AUTHOR_ID','INTERNET_ARCHIVE_ID','DOI','LCCN','OCLC','WIKIDATA_ID','PUBLISHER_SUBMISSION'));
ALTER TABLE external_identifier DROP CONSTRAINT external_identifier_target_type_v8;
ALTER TABLE external_identifier ADD CONSTRAINT external_identifier_target_type_v18 CHECK (
  (identifier_type IN ('ISBN10','ISBN13','GUTENBERG_ID','OPEN_LIBRARY_EDITION_ID','INTERNET_ARCHIVE_ID','LCCN','OCLC','PUBLISHER_SUBMISSION') AND edition_id IS NOT NULL)
  OR (identifier_type='OPEN_LIBRARY_WORK_ID' AND book_id IS NOT NULL)
  OR (identifier_type='OPEN_LIBRARY_AUTHOR_ID' AND author_id IS NOT NULL)
  OR (identifier_type='DOI' AND author_id IS NULL)
  OR identifier_type='WIKIDATA_ID');
ALTER TABLE external_identifier ADD CONSTRAINT publisher_submission_identifier_uuid CHECK (
  identifier_type <> 'PUBLISHER_SUBMISSION' OR identifier_value ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');
