INSERT INTO catalog.license(id,code,name,redistribution_allowed) VALUES
  ('8f000000-0000-4000-8000-000000000051','E2E_CONTENT','Synthetic fixture redistribution',true);
INSERT INTO catalog.book(id, canonical_title, status) VALUES
  ('8f000000-0000-4000-8000-000000000001','EPUB fixture','ACTIVE'),
  ('8f000000-0000-4000-8000-000000000002','Text fixture','ACTIVE');
INSERT INTO catalog.edition(id,book_id,language,title,license_id) VALUES
  ('8f000000-0000-4000-8000-000000000011','8f000000-0000-4000-8000-000000000001','en','EPUB fixture','8f000000-0000-4000-8000-000000000051'),
  ('8f000000-0000-4000-8000-000000000012','8f000000-0000-4000-8000-000000000002','en','Text fixture','8f000000-0000-4000-8000-000000000051');
INSERT INTO catalog.book_asset(id,book_id,edition_id,asset_type,asset_role,source_id,status) VALUES
  ('8f000000-0000-4000-8000-000000000021','8f000000-0000-4000-8000-000000000001','8f000000-0000-4000-8000-000000000011','EPUB','PUBLIC','10000000-0000-4000-8000-000000000005','ACTIVE'),
  ('8f000000-0000-4000-8000-000000000022','8f000000-0000-4000-8000-000000000002','8f000000-0000-4000-8000-000000000012','TXT','PROCESSING','10000000-0000-4000-8000-000000000005','ACTIVE');
INSERT INTO catalog.book_asset_version(id,book_asset_id,version_number,storage_provider,bucket,object_key,original_filename,content_type,size_bytes,sha256,status) VALUES
  ('8f000000-0000-4000-8000-000000000031','8f000000-0000-4000-8000-000000000021',1,'S3','books-public','fixture.epub','fixture.epub','application/epub+zip',:'epub_size',:'epub_sha','AVAILABLE'),
  ('8f000000-0000-4000-8000-000000000032','8f000000-0000-4000-8000-000000000022',1,'S3','books-processing','fixture.txt','fixture.txt','text/plain',:'text_size',:'text_sha','AVAILABLE');
INSERT INTO catalog.book_chapter(id,book_id,edition_id,text_asset_version_id,chapter_key,position,title,start_offset,end_offset,confidence) VALUES
  ('8f000000-0000-4000-8000-000000000041','8f000000-0000-4000-8000-000000000002','8f000000-0000-4000-8000-000000000012',
   '8f000000-0000-4000-8000-000000000032','fixture',1,'Text fixture chapter',0,17,'EXPLICIT');
