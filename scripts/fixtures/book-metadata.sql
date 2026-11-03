INSERT INTO catalog.book(id,canonical_title,original_language,status) VALUES
 ('11111111-1111-4111-8111-111111111111','Classified fixture','en','ACTIVE'),
 ('22222222-2222-4222-8222-222222222222','Unclassified fixture','pt','ACTIVE');
INSERT INTO catalog.source(id,code,name,source_type) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','METADATA_TEST','Metadata fixture','ADMIN_UPLOAD');
INSERT INTO catalog.subject(id,canonical_name,normalized_name,scheme,status) VALUES
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','Adventure','adventure','LCSH','ACTIVE'),
 ('cccccccc-cccc-4ccc-8ccc-cccccccccccc','Inactive category','inactive category','LCSH','INACTIVE');
INSERT INTO catalog.book_subject(book_id,subject_id,source_id,assignment_method) VALUES
 ('11111111-1111-4111-8111-111111111111','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','10000000-0000-4000-8000-000000000001','SOURCE'),
 ('11111111-1111-4111-8111-111111111111','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','CURATED'),
 ('11111111-1111-4111-8111-111111111111','cccccccc-cccc-4ccc-8ccc-cccccccccccc','10000000-0000-4000-8000-000000000001','SOURCE');
