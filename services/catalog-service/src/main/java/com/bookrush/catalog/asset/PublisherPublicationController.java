package com.bookrush.catalog.asset;

import com.bookrush.catalog.book.BookReviewController;
import com.bookrush.catalog.persistence.model.BookAssetAssetRole;
import com.bookrush.catalog.persistence.model.BookAssetAssetType;
import com.bookrush.catalog.storage.ObjectStorage;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Catalog-owned promotion of a verified publisher SOURCE after editorial approval. */
@RestController
@ConditionalOnProperty(name = "storage.enabled", havingValue = "true")
@RequestMapping("/api/admin/books/{bookId}/publisher-publication")
public class PublisherPublicationController {
  private final JdbcTemplate jdbc;
  private final AssetService assets;
  private final ObjectStorage storage;
  private final BookReviewController books;

  public PublisherPublicationController(JdbcTemplate jdbc, AssetService assets, ObjectStorage storage, BookReviewController books) {
    this.jdbc = jdbc; this.assets = assets; this.storage = storage; this.books = books;
  }

  public record Request(UUID submissionId, UUID reviewId, String reviewer, String action) {}

  @PostMapping
  public Map<String, Object> publish(@PathVariable UUID bookId, @RequestBody Request command) throws Exception {
    if (command.submissionId() == null || command.reviewId() == null || command.reviewer() == null || command.reviewer().isBlank()
        || !java.util.Set.of("PUBLISH", "WITHDRAW").contains(String.valueOf(command.action())))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "submission, review and reviewer are required");
    var sources = jdbc.queryForList("""
        SELECT a.id, a.edition_id, a.source_id, a.asset_type, v.bucket, v.object_key,
               v.original_filename, v.content_type, v.sha256
          FROM catalog.external_identifier i
          JOIN catalog.source s ON s.id=i.source_id AND s.code='ADMIN_UPLOAD'
          JOIN catalog.edition e ON e.id=i.edition_id AND e.book_id=?
          JOIN catalog.book_asset a ON a.edition_id=e.id AND a.source_id=s.id AND a.asset_role='SOURCE'
          JOIN catalog.book_asset_version v ON v.book_asset_id=a.id AND v.status='AVAILABLE'
         WHERE i.identifier_type='PUBLISHER_SUBMISSION' AND i.identifier_value=?
         ORDER BY v.created_at DESC LIMIT 1
        """, bookId, command.submissionId().toString());
    if (sources.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "verified publisher source is required");
    var source = sources.getFirst();
    if ("WITHDRAW".equals(command.action())) {
      jdbc.update("UPDATE catalog.book_asset SET status='INACTIVE' WHERE book_id=? AND asset_role='PUBLIC' AND source_id=?", bookId, source.get("source_id"));
      books.revokeApproval(bookId, () -> command.reviewer());
      return Map.of("bookId", bookId, "status", "WITHDRAWN");
    }
    // AssetService deduplicates equal uploads by book/edition/type/role/hash.
    AssetService.UploadResult result;
    try (var input = storage.download(new ObjectStorage.Location(String.valueOf(source.get("bucket")), String.valueOf(source.get("object_key"))))) {
      result = assets.upload(bookId, null, (UUID) source.get("edition_id"), (UUID) source.get("source_id"), null,
          BookAssetAssetType.valueOf(String.valueOf(source.get("asset_type"))), BookAssetAssetRole.PUBLIC,
          String.valueOf(source.get("original_filename")), String.valueOf(source.get("content_type")), input);
    }
    var assetId = result.asset().id();
    jdbc.update("""
        INSERT INTO catalog.rights_decision(id,asset_id,action,territory,distribution_status,evidence_reference,policy_code,actor)
        SELECT ?,?,'DISTRIBUTION','GLOBAL','APPROVED',?,'PUBLISHER_EDITORIAL_APPROVAL',?
        WHERE NOT EXISTS (SELECT 1 FROM catalog.rights_decision WHERE asset_id=? AND policy_code='PUBLISHER_EDITORIAL_APPROVAL' AND distribution_status='APPROVED')
        """, UUID.randomUUID(), assetId, "publisher-review:" + command.reviewId(), command.reviewer(), assetId);
    assets.approve(bookId, assetId);
    books.approve(bookId, () -> command.reviewer());
    return Map.of("bookId", bookId, "assetId", assetId, "status", "PUBLISHED");
  }
}
