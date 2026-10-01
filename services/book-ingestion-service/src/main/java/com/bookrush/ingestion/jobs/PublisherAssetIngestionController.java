package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.catalog.CanonicalCatalogPort;
import com.bookrush.ingestion.catalog.CatalogAssetClient;
import com.bookrush.ingestion.catalog.PublisherSubmissionLinkClient;
import com.bookrush.ingestion.storage.RawStorageProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;

/** Converts a finalized publisher staging object into a catalog source asset. */
@RestController
@RequestMapping("/api/internal/v1/ingestion/publisher-submissions")
public class PublisherAssetIngestionController {
  private final JdbcTemplate jdbc;
  private final S3Client s3;
  private final CanonicalCatalogPort catalog;
  private final CatalogAssetClient assets;
  private final PublisherSubmissionLinkClient links;
  private final String callbackToken;

  public PublisherAssetIngestionController(
      JdbcTemplate jdbc,
      S3Client s3,
      CanonicalCatalogPort catalog,
      CatalogAssetClient assets,
      PublisherSubmissionLinkClient links,
      @Value("${ingestion.publisher-callback.token:}") String callbackToken) {
    this.jdbc = jdbc;
    this.s3 = s3;
    this.catalog = catalog;
    this.assets = assets;
    this.links = links;
    this.callbackToken = callbackToken == null ? "" : callbackToken;
  }

  public record Request(UUID submissionId, String title, String objectKey, String bucket, String contentType, String sha256, UUID ingestionJobId) {}

  @PostMapping("/process")
  public Map<String, Object> process(
      @RequestHeader(value = "X-BookRush-Ingestion-Token", required = false) String supplied,
      @RequestBody Request request) {
    authorize(supplied);
    if (request == null || request.submissionId() == null || request.objectKey() == null
        || request.objectKey().isBlank() || request.title() == null || request.title().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission, title and objectKey are required");
    }
    String type = contentType(request.contentType());
    if (!"EPUB".equals(type) && !"PDF".equals(type)) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "only EPUB and PDF are supported");
    }
    var sourceId = jdbc.queryForObject("select id from catalog.source where code='ADMIN_UPLOAD' and is_active", UUID.class);
    var operation = "publisher-upload:" + request.submissionId();
    var suppliedHash = request.sha256() == null ? "" : request.sha256().toLowerCase();
    var result = catalog.apply(new CanonicalCatalogPort.CanonicalCatalogCommand(
        operation, sha256(operation + "\u0000" + suppliedHash + "\u0000" + request.title()), 0L,
        "ADMIN_UPLOAD", "PUBLISHER_SUBMISSION", request.submissionId().toString(), request.title(), null,
        "Publisher upload", Map.of("objectKey", request.objectKey(), "bucket", request.bucket() == null ? "" : request.bucket()), null, null, null, null));
    var bookId = UUID.fromString(String.valueOf(result.get("bookId")));
    var editionId = UUID.fromString(String.valueOf(result.get("editionId")));
    Path temp = null;
    try {
      temp = Files.createTempFile("bookrush-publisher-", type.toLowerCase());
      String bucket = request.bucket() == null || request.bucket().isBlank() ? "books-staging" : request.bucket();
      try (InputStream in = s3.getObject(r -> r.bucket(bucket).key(request.objectKey()))) {
        Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      if (request.sha256() != null && !request.sha256().isBlank() && !request.sha256().equalsIgnoreCase(sha256(temp))) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "staged object hash mismatch");
      }
      assets.uploadSource(bookId, editionId, sourceId, temp, Path.of(request.objectKey()).getFileName().toString(), type);
      links.link(request.submissionId(), bookId, request.ingestionJobId(), "ADMIN_UPLOAD", request.submissionId().toString());
      return Map.of("submissionId", request.submissionId(), "catalogBookId", bookId, "status", "PROCESSING");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("publisher object ingestion failed", e);
    } finally {
      if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
    }
  }

  private void authorize(String supplied) {
    if (callbackToken.isBlank() || supplied == null || !MessageDigest.isEqual(
        supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8), callbackToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ingestion callback credential");
    }
  }

  private String contentType(String contentType) {
    if (contentType == null) return "";
    if (contentType.contains("pdf")) return "PDF";
    if (contentType.contains("epub")) return "EPUB";
    return "";
  }

  private String sha256(Path file) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(file)) {
      var buffer = new byte[8192];
      for (int read; (read = in.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
}
