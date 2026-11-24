package com.bookrush.publisherservice.api;
import java.security.Principal; import java.util.*; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import org.springframework.http.HttpStatus; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import com.bookrush.publisherservice.BehaviorEventPublisher;
@RestController @RequestMapping("/api/v1/publisher") public class PublisherController { private final JdbcTemplate jdbc; private final com.bookrush.publisherservice.PublisherIngestionClient ingestion; private final BehaviorEventPublisher behavior; public PublisherController(JdbcTemplate jdbc, com.bookrush.publisherservice.PublisherIngestionClient ingestion, BehaviorEventPublisher behavior){this.jdbc=jdbc;this.ingestion=ingestion;this.behavior=behavior;} private String s(Principal p){if(p==null||p.getName()==null||p.getName().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"authentication required"); return p.getName();} private void membership(String subject){jdbc.update("INSERT INTO publisher.membership(subject_key) VALUES (?) ON CONFLICT DO NOTHING",subject); if(!"ACTIVE".equals(jdbc.queryForObject("SELECT status FROM publisher.membership WHERE subject_key=?",String.class,subject))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"publisher membership is not active");} private void operator(Authentication a){if(a==null||a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("ROLE_platform-admins")||(x.getAuthority().equals("ROLE_operators")||x.getAuthority().equals("ROLE_platform-operators"))))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"operator permission required");} public record Submission(@NotBlank @Size(max=300) String title, UUID catalogBookId, String sourceCode, String sourceExternalId){}
 @org.springframework.beans.factory.annotation.Value("${publisher.storage.bucket:books-staging}")
 private String stagingBucket = "books-staging";
 @PostMapping("/submissions") public Object create(@Valid @RequestBody Submission req,Principal p,HttpServletRequest request){String subject=s(p);membership(subject);UUID id=UUID.randomUUID();jdbc.update("insert into publisher.submission(id,subject_key,title,catalog_book_id,source_code,source_external_id) values(?,?,?,?,?,?)",id,subject,req.title(),req.catalogBookId(),req.sourceCode(),req.sourceExternalId());behavior.publish("PUBLISHER_SUBMISSION_CREATED",req.catalogBookId(),Map.of("submissionId",id),request.getHeader("Authorization"));return Map.of("id",id,"title",req.title(),"status","DRAFT");}
 @PostMapping("/submissions/{id}/submit")
 public Object submit(@PathVariable UUID id, Principal p, HttpServletRequest request) {
   String subject = s(p);
   membership(subject);
   var rows = jdbc.queryForList(
       "SELECT title,status FROM publisher.submission WHERE id=? AND subject_key=?", id, subject);
   if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
   var row = rows.getFirst();
   String status = String.valueOf(row.get("status"));
   if (Set.of("SUBMITTED", "PROCESSING").contains(status)) {
     if ("PROCESSING".equals(status)) enqueueReview(id);
     return Map.of("id", id, "status", status);
   }
   if (!"UPLOADED".equals(status))
     throw new ResponseStatusException(HttpStatus.CONFLICT, "a finalized upload is required before review");
   var uploads = jdbc.queryForList(
       "SELECT object_key,content_type,sha256 FROM publisher.staged_upload WHERE submission_id=? AND subject_key=? AND status='FINALIZED' ORDER BY finalized_at DESC LIMIT 1", id, subject);
   if (uploads.isEmpty())
     throw new ResponseStatusException(HttpStatus.CONFLICT, "a finalized upload is required before review");
   int claimed = jdbc.update(
       "UPDATE publisher.submission SET status='SUBMITTED',updated_at=now() WHERE id=? AND subject_key=? AND status='UPLOADED'", id, subject);
   if (claimed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "submission state changed; refresh and retry");
   var upload = uploads.getFirst();
   try {
     ingestion.process(id, String.valueOf(row.get("title")), String.valueOf(upload.get("object_key")),
         stagingBucket, String.valueOf(upload.get("content_type")), String.valueOf(upload.get("sha256")));
   } catch (RuntimeException failure) {
     // Do not undo a successful ingestion callback if only the response was lost.
     jdbc.update("UPDATE publisher.submission SET status='UPLOADED',updated_at=now() WHERE id=? AND subject_key=? AND status='SUBMITTED'", id, subject);
     if (failure instanceof org.springframework.web.client.RestClientResponseException response && Set.of(400,415,422).contains(response.getStatusCode().value()))
       throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"book validation failed; upload a valid file");
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ingestion unavailable; refresh and retry");
   }
   jdbc.update("UPDATE publisher.submission SET status='PROCESSING',updated_at=now() WHERE id=? AND subject_key=? AND status='SUBMITTED'", id, subject);
   enqueueReview(id);
   behavior.publish("PUBLISHER_SUBMISSION_SUBMITTED", null, Map.of("submissionId", id, "status", "PROCESSING"), request.getHeader("Authorization"));
   return Map.of("id", id, "status", "PROCESSING");
 }
 private void enqueueReview(UUID id) {
   jdbc.update("INSERT INTO publisher.publication_review(id,submission_id,status,reviewer_subject_key) SELECT ?,?,'PENDING','system' WHERE NOT EXISTS (SELECT 1 FROM publisher.publication_review WHERE submission_id=? AND status='PENDING') ON CONFLICT (id) DO NOTHING", UUID.nameUUIDFromBytes(("publisher-initial-review:" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)), id, id);
 }
 @GetMapping("/submissions") public Object list(Principal p){return jdbc.queryForList("select id,title,status,catalog_book_id,source_code,source_external_id,ingestion_job_id,linked_at,created_at,updated_at from publisher.submission where subject_key=? order by created_at desc",s(p));}
 @GetMapping("/submissions/{id}") public Object get(@PathVariable UUID id,Principal p){var rows=jdbc.queryForList("select id,title,status,catalog_book_id,source_code,source_external_id,ingestion_job_id,linked_at,created_at,updated_at from publisher.submission where id=? and subject_key=?",id,s(p)); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"submission not found"); return rows.getFirst();}
 @GetMapping("/submissions/{id}/reviews") public Object reviewHistory(@PathVariable UUID id,Principal p){String subject=s(p);if(jdbc.queryForObject("select count(*) from publisher.submission where id=? and subject_key=?",Integer.class,id,subject)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"submission not found");return jdbc.queryForList("select id,status,reviewer_subject_key,reason_code,notes,created_at from publisher.publication_review where submission_id=? order by created_at desc",id);}
 @PostMapping("/submissions/{id}/withdraw")
 @org.springframework.transaction.annotation.Transactional
 public Object withdraw(@PathVariable UUID id,Principal p,HttpServletRequest request) {
   String subject=s(p);
   var rows=jdbc.queryForList("SELECT book_id FROM publisher.published_work WHERE submission_id=? AND publisher_subject_key=? AND status='PUBLISHED' FOR UPDATE",id,subject);
   if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"published submission not found");
   ingestion.publication(id,(UUID)rows.getFirst().get("book_id"),UUID.randomUUID(),subject,"WITHDRAW");
   jdbc.update("UPDATE publisher.published_work SET status='WITHDRAWN',withdrawn_at=now() WHERE submission_id=? AND publisher_subject_key=?",id,subject);
   jdbc.update("UPDATE publisher.submission SET status='WITHDRAWN',updated_at=now() WHERE id=? AND subject_key=?",id,subject);
   behavior.publish("PUBLISHER_WORK_WITHDRAWN",null,Map.of("submissionId",id),request.getHeader("Authorization"));
   return Map.of("submissionId",id,"status","WITHDRAWN");
 }
 public record Metadata(String synopsis, String language, String isbn, String publisherName, List<String> keywords) {}
 @PutMapping("/submissions/{id}/metadata") public Object metadata(@PathVariable UUID id,@RequestBody Metadata req,Principal p){String subject=s(p);membership(subject);requireOwnedDraft(id,subject);String keywords=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(req.keywords()==null?List.of():req.keywords()).toString();int n=jdbc.update("INSERT INTO publisher.submission_metadata(submission_id,synopsis,language,isbn,publisher_name,keywords,updated_at) VALUES (?,?,?,?,?,?::jsonb,now()) ON CONFLICT (submission_id) DO UPDATE SET synopsis=excluded.synopsis,language=excluded.language,isbn=excluded.isbn,publisher_name=excluded.publisher_name,keywords=excluded.keywords,updated_at=now()",id,req.synopsis(),req.language(),req.isbn(),req.publisherName(),keywords);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"submission not found");return Map.of("submissionId",id,"updated",true);}
 public record Rights(String rightType,String territory,String evidenceReference) {}
 @PostMapping("/submissions/{id}/rights") public Object rights(@PathVariable UUID id,@Valid @RequestBody Rights req,Principal p){String subject=s(p);membership(subject);requireOwnedDraft(id,subject);if(!Set.of("DISTRIBUTION","PROCESSING","DISPLAY").contains(String.valueOf(req.rightType())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid right type");UUID rightId=UUID.randomUUID();jdbc.update("INSERT INTO publisher.rights_attestation(id,submission_id,right_type,territory,evidence_reference,affirmed_by_subject) VALUES (?,?,?,?,?,?) ON CONFLICT (submission_id,right_type,territory) DO UPDATE SET evidence_reference=excluded.evidence_reference,affirmed_by_subject=excluded.affirmed_by_subject,affirmed_at=now()",rightId,id,req.rightType(),req.territory()==null?"GLOBAL":req.territory(),req.evidenceReference(),subject);return Map.of("submissionId",id,"rightType",req.rightType(),"affirmed",true);}
 @GetMapping("/review") public Object review(Principal p,Authentication a){operator(a);s(p);return jdbc.queryForList("SELECT r.id,r.submission_id,s.title,r.status,r.reviewer_subject_key,r.reason_code,r.notes,r.created_at FROM publisher.publication_review r JOIN publisher.submission s ON s.id=r.submission_id WHERE r.status='PENDING' AND NOT EXISTS (SELECT 1 FROM publisher.publication_review decided WHERE decided.submission_id=r.submission_id AND decided.status<>'PENDING' AND decided.created_at>=r.created_at) ORDER BY r.created_at");}
 public record Review(String status,String reasonCode,String notes) {}
 @PostMapping("/submissions/{id}/review")
 @org.springframework.transaction.annotation.Transactional
 public Object review(@PathVariable UUID id, @RequestBody Review req, Principal p, Authentication a) {
   operator(a);
   String reviewer=s(p);
   if (!Set.of("APPROVED","REJECTED","CHANGES_REQUESTED").contains(String.valueOf(req.status())))
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"unsupported review status");
   var rows=jdbc.queryForList("SELECT subject_key,catalog_book_id,status FROM publisher.submission WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"submission not found");
   var row=rows.getFirst();
   if("PUBLISHED".equals(row.get("status")) && "APPROVED".equals(req.status())) return Map.of("status","APPROVED");
   if(!"PROCESSING".equals(row.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"submission is not ready for review");
   UUID reviewId=UUID.randomUUID();
   if("APPROVED".equals(req.status())) {
     if(row.get("catalog_book_id")==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"verified catalog link required");
     if(jdbc.queryForObject("SELECT count(*) FROM publisher.rights_attestation WHERE submission_id=? AND right_type='DISTRIBUTION' AND territory='GLOBAL' AND affirmed_by_subject=?",Integer.class,id,row.get("subject_key"))==0)
       throw new ResponseStatusException(HttpStatus.CONFLICT,"distribution attestation required");
     ingestion.publication(id,(UUID)row.get("catalog_book_id"),reviewId,reviewer,"PUBLISH");
     jdbc.update("INSERT INTO publisher.published_work(id,submission_id,book_id,publisher_subject_key) VALUES (?,?,?,?) ON CONFLICT(submission_id) DO UPDATE SET status='PUBLISHED',withdrawn_at=NULL",UUID.randomUUID(),id,row.get("catalog_book_id"),row.get("subject_key"));
   }
   jdbc.update("INSERT INTO publisher.publication_review(id,submission_id,status,reviewer_subject_key,reason_code,notes) VALUES (?,?,?,?,?,?)",reviewId,id,req.status(),reviewer,req.reasonCode(),req.notes());
   String next="APPROVED".equals(req.status())?"PUBLISHED":"REJECTED".equals(req.status())?"REJECTED":"CHANGES_REQUESTED";
   jdbc.update("UPDATE publisher.submission SET status=?,updated_at=now() WHERE id=?",next,id);
   return Map.of("reviewId",reviewId,"status",req.status());
 }
 private void requireOwnedDraft(UUID id,String subject) {
   var rows=jdbc.queryForList("SELECT status FROM publisher.submission WHERE id=? AND subject_key=?",id,subject);
   if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"submission not found");
   if(!Set.of("DRAFT","UPLOADED").contains(String.valueOf(rows.getFirst().get("status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"submission is not editable");
 }
 @GetMapping("/membership") public Object membership(Principal p){String subject=s(p);membership(subject);return jdbc.queryForMap("SELECT subject_key,status,display_name,created_at,updated_at FROM publisher.membership WHERE subject_key=?",subject);}
}
