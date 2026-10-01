package com.bookrush.publisherservice.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Internal, idempotent callback used by ingestion after canonicalization. */
@RestController
@RequestMapping("/api/internal/v1/publisher/submissions")
public class PublisherIngestionLinkController {
  private final JdbcTemplate jdbc;
  private final String callbackToken;

  public PublisherIngestionLinkController(JdbcTemplate jdbc, @Value("${publisher.ingestion-callback-token:}") String callbackToken) {
    this.jdbc = jdbc;
    this.callbackToken = callbackToken == null ? "" : callbackToken;
  }

  public record LinkRequest(UUID catalogBookId, UUID ingestionJobId, String sourceCode, String sourceExternalId) {}

  @PostMapping("/{submissionId}/catalog-link")
  public Map<String, Object> link(
      @PathVariable UUID submissionId,
      @RequestHeader(value = "X-BookRush-Ingestion-Token", required = false) String supplied,
      @RequestBody LinkRequest request) {
    if (callbackToken.isBlank() || supplied == null || !MessageDigest.isEqual(
        supplied.getBytes(StandardCharsets.UTF_8), callbackToken.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid ingestion callback credential");
    }
    if (request == null || request.catalogBookId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalogBookId is required");
    }
    var rows = jdbc.queryForList("select catalog_book_id from publisher.submission where id=?", submissionId);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
    var current = rows.getFirst().get("catalog_book_id");
    if (current != null && !request.catalogBookId().toString().equals(String.valueOf(current))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "submission is already linked to another catalog book");
    }
    jdbc.update(
        "update publisher.submission set catalog_book_id=?, ingestion_job_id=coalesce(?,ingestion_job_id), source_code=coalesce(?,source_code), source_external_id=coalesce(?,source_external_id), status=case when status in ('DRAFT','UPLOADED','SUBMITTED') then 'PROCESSING' else status end, linked_at=coalesce(linked_at,now()), updated_at=now() where id=?",
        request.catalogBookId(), request.ingestionJobId(), request.sourceCode(), request.sourceExternalId(), submissionId);
    return Map.of("submissionId", submissionId, "catalogBookId", request.catalogBookId(), "status", "PROCESSING");
  }
}
