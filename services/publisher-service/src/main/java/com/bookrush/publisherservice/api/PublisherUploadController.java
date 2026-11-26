package com.bookrush.publisherservice.api;

import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static com.bookrush.publisherservice.api.PublisherOwnership.OWN;

@RestController
@RequestMapping("/api/v1/publisher/submissions/{submissionId}/upload")
@ConditionalOnBean(S3Presigner.class)
public class PublisherUploadController {
  private final JdbcTemplate db;
  private final S3Presigner signer;
  private final S3Client s3;
  private final StorageProperties properties;
  private final PublisherIdentity identities;
  private final PublisherOwnership ownership;

  PublisherUploadController(JdbcTemplate db, S3Presigner signer, S3Client s3, StorageProperties properties,
      PublisherIdentity identities, PublisherOwnership ownership) {
    this.db = db;
    this.signer = signer;
    this.s3 = s3;
    this.properties = properties;
    this.identities = identities;
    this.ownership = ownership;
  }

  @PostMapping
  @Transactional
  public Object request(@PathVariable UUID submissionId, @RequestBody Map<String, Object> body, Principal principal) {
    var actor = identities.author(principal);
    ownership.lock(submissionId, actor).requireEditable();
    String filename = String.valueOf(body.getOrDefault("filename", "book.bin")).replaceAll("[^A-Za-z0-9._-]", "_");
    String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"));
    UUID id = UUID.randomUUID();
    String key = "publisher/" + submissionId + "/" + id + "-" + filename;
    db.update("""
        INSERT INTO publisher.staged_upload(id,submission_id,subject_key,identity_issuer,identity_subject,object_key,content_type)
        VALUES (?,?,?,?,?,?,?)
        """, id, submissionId, actor.subject(), actor.issuer(), actor.subject(), key, contentType);
    var put = PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(contentType).build();
    var signed = signer.presignPutObject(PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofSeconds(properties.getTtlSeconds())).putObjectRequest(put).build());
    return Map.of("uploadId", id, "url", signed.url().toString(), "expiresAt", signed.expiration());
  }

  @PostMapping("/{uploadId}/finalize")
  @Transactional
  public Object finalizeUpload(@PathVariable UUID submissionId, @PathVariable UUID uploadId,
      @RequestBody Map<String, Object> body, Principal principal) {
    var actor = identities.author(principal);
    var submission = ownership.lock(submissionId, actor);
    var rows = db.queryForList("""
        SELECT object_key,status,sha256,size_bytes FROM publisher.staged_upload
        WHERE id=? AND submission_id=? AND identity_issuer=? AND identity_subject=? FOR UPDATE
        """, uploadId, submissionId, actor.issuer(), actor.subject());
    if (rows.isEmpty()) throw missingUpload();
    var upload = rows.getFirst();
    String hash = String.valueOf(body.getOrDefault("sha256", ""));
    if ("FINALIZED".equals(upload.get("status"))) {
      if (!hash.equals(upload.get("sha256"))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "upload already finalized with different metadata");
      }
      return receipt(uploadId, upload.get("size_bytes"), hash);
    }
    if (!"PENDING".equals(upload.get("status"))) throw missingUpload();
    submission.requireEditable();
    String key = String.valueOf(upload.get("object_key"));
    Long size = s3.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).contentLength();
    int changed = db.update("UPDATE publisher.staged_upload SET status='FINALIZED',sha256=?,size_bytes=?,finalized_at=now() "
        + "WHERE id=? AND submission_id=? AND " + OWN + " AND status='PENDING'",
        hash, size, uploadId, submissionId, actor.issuer(), actor.subject());
    if (changed == 0) throw missingUpload();
    db.update("UPDATE publisher.submission SET status='UPLOADED',updated_at=now() WHERE id=? AND "
        + OWN + " AND status='DRAFT'", submissionId, actor.issuer(), actor.subject());
    return receipt(uploadId, size, hash);
  }

  private static Map<String, Object> receipt(UUID id, Object size, String hash) {
    return Map.of("uploadId", id, "status", "FINALIZED", "sizeBytes", size, "sha256", hash);
  }

  private static ResponseStatusException missingUpload() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "upload not found");
  }
}
