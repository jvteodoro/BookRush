-- Run against catalog-fixture.sql in a disposable database, never production.
-- 1. Gutenberg ID -> work (edition identity is preserved).
SELECT b.id, b.canonical_title, e.id AS edition_id
FROM catalog.external_identifier i JOIN catalog.source s ON s.id=i.source_id
JOIN catalog.edition e ON e.id=i.edition_id JOIN catalog.book b ON b.id=e.book_id
WHERE s.code='GUTENBERG' AND i.identifier_type='GUTENBERG_ID' AND i.identifier_value='1342';

-- 2. Assets of a work.
SELECT * FROM catalog.book_asset
WHERE book_id='30000000-0000-4000-8000-000000000001' ORDER BY id;

-- 3. Latest AVAILABLE version of each active public EPUB asset.
-- PUBLIC role alone is not legal authorization: apply distribution policy before returning URLs.
SELECT DISTINCT ON (a.id) a.id AS asset_id, v.id, v.bucket, v.object_key
FROM catalog.book_asset a JOIN catalog.book_asset_version v ON v.book_asset_id=a.id
WHERE a.book_id='30000000-0000-4000-8000-000000000001'
AND a.asset_type='EPUB' AND a.asset_role='PUBLIC' AND a.status='ACTIVE' AND v.status='AVAILABLE'
ORDER BY a.id, v.version_number DESC;

-- 4. Versions including operational failures.
SELECT * FROM catalog.book_asset_version
WHERE book_asset_id='40000000-0000-4000-8000-000000000002' ORDER BY version_number DESC;

-- 5. Matching content; hash is intentionally not unique.
SELECT * FROM catalog.book_asset_version WHERE sha256=repeat('d',64) ORDER BY id;

-- 6. Failed attempts in latest job of a source, with deterministic tie breaker.
SELECT i.* FROM catalog.ingestion_item i
WHERE i.status='FAILED' AND i.ingestion_job_id=(
SELECT j.id FROM catalog.ingestion_job j JOIN catalog.source s ON s.id=j.source_id
WHERE s.code='GUTENBERG' ORDER BY j.created_at DESC,j.id DESC LIMIT 1)
ORDER BY i.external_identifier,i.attempt_number;

-- 7. Descendant lineage; report a cycle once and stop expanding its branch.
WITH RECURSIVE lineage AS (
SELECT p.id, p.input_asset_version_id, p.output_asset_version_id, p.processing_type,
ARRAY[p.input_asset_version_id,p.output_asset_version_id] AS path, false AS is_cycle, 1 AS depth
FROM catalog.asset_processing p
WHERE p.input_asset_version_id='50000000-0000-4000-8000-000000000001' AND p.status='SUCCEEDED'
UNION ALL
SELECT p.id,p.input_asset_version_id,p.output_asset_version_id,p.processing_type,
l.path || p.output_asset_version_id,p.output_asset_version_id=ANY(l.path),l.depth+1
FROM lineage l JOIN catalog.asset_processing p ON p.input_asset_version_id=l.output_asset_version_id
WHERE NOT l.is_cycle AND p.status='SUCCEEDED'
)
SELECT * FROM lineage ORDER BY depth,id;

-- 8. Trace original metadata and generic source from a physical version.
SELECT v.id, a.source_id, i.id AS ingestion_item_id, r.id AS source_record_id, r.raw_metadata
FROM catalog.book_asset_version v JOIN catalog.book_asset a ON a.id=v.book_asset_id
LEFT JOIN catalog.ingestion_item i ON i.id=v.ingestion_item_id
LEFT JOIN catalog.source_record r ON r.id=i.source_record_id
WHERE v.id='50000000-0000-4000-8000-000000000001';
