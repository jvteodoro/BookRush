INSERT INTO catalog.book(id,canonical_title,first_publication_year,status) VALUES
 ('11111111-1111-4111-8111-111111111111','Alpha Harbour',1899,'ACTIVE'),
 ('22222222-2222-4222-8222-222222222222','Beta Harbour',NULL,'REVIEW_REQUIRED'),
 ('33333333-3333-4333-8333-333333333333','Gamma Archive',1900,'ACTIVE'),
 ('44444444-4444-4444-8444-444444444444','Undated Work',NULL,'ACTIVE');
INSERT INTO catalog.edition(id,book_id,publication_year) VALUES
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','11111111-1111-4111-8111-111111111111',1901),
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','22222222-2222-4222-8222-222222222222',1912),
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3','22222222-2222-4222-8222-222222222222',1920);
INSERT INTO catalog.author(id,name) VALUES
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','Fixture Writer'),
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2','Translator Only');
INSERT INTO catalog.book_author(book_id,author_id,role,position) VALUES
 ('11111111-1111-4111-8111-111111111111','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','AUTHOR',1),
 ('22222222-2222-4222-8222-222222222222','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','AUTHOR',1),
 ('33333333-3333-4333-8333-333333333333','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2','TRANSLATOR',1);
INSERT INTO catalog.subject(id,canonical_name,normalized_name,scheme,status) VALUES
 ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1','Category: Adventure','adventure','GUTENBERG','ACTIVE'),
 ('cccccccc-cccc-4ccc-8ccc-ccccccccccc2','Romance','romance','LCSH','ACTIVE'),
 ('cccccccc-cccc-4ccc-8ccc-ccccccccccc3','Hidden category','hidden category','LCSH','INACTIVE'),
 ('cccccccc-cccc-4ccc-8ccc-ccccccccccc4','application/rdf+xml','application/rdf+xml','GUTENBERG','ACTIVE'),
 ('cccccccc-cccc-4ccc-8ccc-ccccccccccc5','PR','pr','GUTENBERG','ACTIVE');
INSERT INTO catalog.book_subject(book_id,subject_id,source_id,assignment_method) VALUES
 ('11111111-1111-4111-8111-111111111111','cccccccc-cccc-4ccc-8ccc-ccccccccccc1','10000000-0000-4000-8000-000000000001','SOURCE'),
 ('22222222-2222-4222-8222-222222222222','cccccccc-cccc-4ccc-8ccc-ccccccccccc2','10000000-0000-4000-8000-000000000001','SOURCE'),
 ('33333333-3333-4333-8333-333333333333','cccccccc-cccc-4ccc-8ccc-ccccccccccc3','10000000-0000-4000-8000-000000000001','SOURCE'),
 ('33333333-3333-4333-8333-333333333333','cccccccc-cccc-4ccc-8ccc-ccccccccccc4','10000000-0000-4000-8000-000000000001','SOURCE'),
 ('33333333-3333-4333-8333-333333333333','cccccccc-cccc-4ccc-8ccc-ccccccccccc5','10000000-0000-4000-8000-000000000001','SOURCE');
