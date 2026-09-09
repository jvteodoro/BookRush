-- Test data only. Physical keys are references; no remote objects are downloaded.
INSERT INTO catalog.book(id,canonical_title,status) VALUES ('30000000-0000-4000-8000-000000000001','Pride and Prejudice','ACTIVE');
INSERT INTO catalog.author(id,name) VALUES ('30000000-0000-4000-8000-000000000002','Jane Austen');
INSERT INTO catalog.book_author(book_id,author_id,role,position) VALUES ('30000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000002','AUTHOR',1);
INSERT INTO catalog.edition(id,book_id,language,title,license_id) VALUES ('30000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000001','en','English public-domain edition','20000000-0000-4000-8000-000000000001');
INSERT INTO catalog.external_identifier(id,source_id,edition_id,identifier_type,identifier_value) VALUES ('30000000-0000-4000-8000-000000000004','10000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000003','GUTENBERG_ID','1342');
INSERT INTO catalog.source_record(id,source_id,external_id,raw_metadata,retrieved_at,content_hash)
VALUES ('30000000-0000-4000-8000-000000000005','10000000-0000-4000-8000-000000000001','1342','{"title":"Pride and Prejudice"}',now(),repeat('a',64));
INSERT INTO catalog.ingestion_job(id,source_id,status,trigger_type,items_discovered,items_processed,items_succeeded,items_failed)
VALUES ('30000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000001','COMPLETED_WITH_ERRORS','MANUAL',2,2,1,1);
INSERT INTO catalog.ingestion_item(id,ingestion_job_id,source_id,external_identifier,source_record_id,book_id,edition_id,status)
VALUES ('30000000-0000-4000-8000-000000000007','30000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000001','1342','30000000-0000-4000-8000-000000000005','30000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000003','SUCCEEDED');
INSERT INTO catalog.ingestion_item(id,ingestion_job_id,source_id,external_identifier,status,error_code)
VALUES ('30000000-0000-4000-8000-000000000008','30000000-0000-4000-8000-000000000006','10000000-0000-4000-8000-000000000001','11','FAILED','SOURCE_UNAVAILABLE');
INSERT INTO catalog.book_asset(id,book_id,edition_id,asset_type,asset_role,source_id)
SELECT ('40000000-0000-4000-8000-00000000000'||n)::uuid,'30000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000003',
CASE n WHEN 3 THEN 'TXT' WHEN 4 THEN 'JSON' ELSE 'EPUB' END,
CASE n WHEN 1 THEN 'SOURCE' WHEN 2 THEN 'PUBLIC' ELSE 'PROCESSING' END,
CASE n WHEN 1 THEN '10000000-0000-4000-8000-000000000001'::uuid ELSE '10000000-0000-4000-8000-000000000005'::uuid END
FROM generate_series(1,4) n;
INSERT INTO catalog.book_asset_version(id,book_asset_id,version_number,storage_provider,bucket,object_key,content_type,size_bytes,sha256,status,ingestion_item_id)
SELECT ('50000000-0000-4000-8000-00000000000'||n)::uuid,('40000000-0000-4000-8000-00000000000'||n)::uuid,1,'S3',
CASE n WHEN 1 THEN 'books-source' WHEN 2 THEN 'books-public' ELSE 'books-processing' END,
'books/30000000-0000-4000-8000-000000000001/fixture/'||n||'/v1/book',
CASE n WHEN 3 THEN 'text/plain' WHEN 4 THEN 'application/json' ELSE 'application/epub+zip' END,
10,repeat('d',64),'AVAILABLE',CASE n WHEN 1 THEN '30000000-0000-4000-8000-000000000007'::uuid END
FROM generate_series(1,4) n;
INSERT INTO catalog.book_asset_version(id,book_asset_id,version_number,storage_provider,bucket,object_key,status)
VALUES ('50000000-0000-4000-8000-000000000005','40000000-0000-4000-8000-000000000002',2,'S3','books-public','fixture/failed-v2','FAILED');
INSERT INTO catalog.asset_processing(id,input_asset_version_id,output_asset_version_id,processing_type,processor,processor_version,status)
SELECT ('60000000-0000-4000-8000-00000000000'||n)::uuid,
('50000000-0000-4000-8000-00000000000'||n)::uuid,('50000000-0000-4000-8000-00000000000'||(n+1))::uuid,
CASE n WHEN 1 THEN 'NORMALIZATION' WHEN 2 THEN 'TEXT_EXTRACTION' ELSE 'CHAPTER_SEGMENTATION' END,
'fixture-processor','1','SUCCEEDED' FROM generate_series(1,3) n;
