package com.bookrush.publisherservice.api;

import java.security.Principal;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;

@RestController @RequestMapping("/api/v1/publisher/submissions/{submissionId}/upload")
@ConditionalOnProperty(name = "publisher.storage.enabled", havingValue = "true")
public class PublisherUploadController {
  private final JdbcTemplate jdbc; private final S3Presigner signer; private final S3Client s3; private final StorageProperties props;
  public PublisherUploadController(JdbcTemplate jdbc,S3Presigner signer,S3Client s3,StorageProperties props){this.jdbc=jdbc;this.signer=signer;this.s3=s3;this.props=props;}
  @org.springframework.beans.factory.annotation.Value("${publisher.storage.max-upload-bytes:104857600}")
  private long maxUploadBytes=104857600;
  private String subject(Principal p){if(p==null||p.getName()==null||p.getName().isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"authentication required");return p.getName();}
  private void requireEditable(UUID id, String subject) {
    var rows = jdbc.queryForList("SELECT status FROM publisher.submission WHERE id=? AND subject_key=?", id, subject);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
    if (!Set.of("DRAFT", "UPLOADED").contains(String.valueOf(rows.getFirst().get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "submission is no longer editable");
    if (!"ACTIVE".equals(jdbc.queryForObject("SELECT status FROM publisher.membership WHERE subject_key=?", String.class, subject)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "publisher membership is not active");
  }
  @PostMapping public Object request(@PathVariable UUID submissionId,@RequestBody Map<String,Object> body,Principal p){String sub=subject(p); requireEditable(submissionId,sub); String name=String.valueOf(body.getOrDefault("filename","book.bin")).replaceAll("[^A-Za-z0-9._-]","_"); String type=String.valueOf(body.getOrDefault("contentType","application/octet-stream")); if(!Set.of("application/epub+zip","application/pdf").contains(type)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"only EPUB and PDF are supported"); UUID id=UUID.randomUUID(); String key="publisher/"+sub+"/"+submissionId+"/"+id+"-"+name; jdbc.update("INSERT INTO publisher.staged_upload(id,submission_id,subject_key,object_key,content_type) VALUES (?,?,?,?,?)",id,submissionId,sub,key,type); var request=PutObjectRequest.builder().bucket(props.getBucket()).key(key).contentType(type).build(); var signed=signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(props.getTtlSeconds())).putObjectRequest(request).build()); return Map.of("uploadId",id,"url",signed.url().toString(),"expiresAt",signed.expiration()); }
  @PostMapping("/{uploadId}/finalize") public Object finalizeUpload(@PathVariable UUID submissionId,@PathVariable UUID uploadId,@RequestBody Map<String,Object> body,Principal p){String sub=subject(p); requireEditable(submissionId,sub); String hash=String.valueOf(body.getOrDefault("sha256","")); if(!hash.matches("[0-9a-f]{64}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"valid SHA-256 is required"); var rows=jdbc.queryForList("SELECT object_key,content_type FROM publisher.staged_upload WHERE id=? AND submission_id=? AND subject_key=? AND status='PENDING'",uploadId,submissionId,sub); if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"upload not found"); String key=String.valueOf(rows.getFirst().get("object_key")); var head=s3.headObject(HeadObjectRequest.builder().bucket(props.getBucket()).key(key).build()); Long size=head.contentLength(); if(size!=null&&size>maxUploadBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"uploaded object exceeds the limit"); if(size==null||size<=0) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"uploaded object is empty"); jdbc.update("UPDATE publisher.staged_upload SET status='FINALIZED',sha256=?,size_bytes=?,finalized_at=now() WHERE id=?",hash,size,uploadId); jdbc.update("UPDATE publisher.submission SET status='UPLOADED',updated_at=now() WHERE id=? AND subject_key=? AND status='DRAFT'",submissionId,sub); return Map.of("uploadId",uploadId,"status","FINALIZED","sizeBytes",size,"sha256",hash); }
}
