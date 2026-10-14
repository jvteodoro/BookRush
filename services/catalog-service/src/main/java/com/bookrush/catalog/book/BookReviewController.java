package com.bookrush.catalog.book;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administrative review boundary for imported books and their physical assets. */
@RestController
@RequestMapping("/api/admin/books")
public class BookReviewController {
  private static final int MAX_PAGE_SIZE = 100;
  private final JdbcTemplate jdbc;

  public BookReviewController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/review")
  public ReviewPage review(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
    }
    Long totalResult =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog.book WHERE status='REVIEW_REQUIRED'", Long.class);
    long total = totalResult == null ? 0L : totalResult;
    int offset = Math.multiplyExact(page, size);
    List<BookReview> items =
        jdbc.query(
            """
            SELECT id, canonical_title, original_title, original_language,
                   first_publication_year, description, status, updated_at
            FROM catalog.book
            WHERE status='REVIEW_REQUIRED'
            ORDER BY updated_at DESC, id
            LIMIT ? OFFSET ?
            """,
            (rs, row) ->
                new BookReview(
                    rs.getObject("id", UUID.class),
                    rs.getString("canonical_title"),
                    rs.getString("original_title"),
                    rs.getString("original_language"),
                    rs.getObject("first_publication_year", Short.class),
                    rs.getString("description"),
                    rs.getString("status"),
                    toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
                    assets(rs.getObject("id", UUID.class))),
            size,
            offset);
    return new ReviewPage(items, page, size, total, offset + items.size() >= total);
  }

  @PostMapping("/{bookId}/approve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void approve(@PathVariable UUID bookId) {
    int changed =
        jdbc.update(
            "UPDATE catalog.book SET status='ACTIVE' WHERE id=? AND status='REVIEW_REQUIRED'",
            bookId);
    if (changed > 0) return;
    String status =
        jdbc.query(
                "SELECT status FROM catalog.book WHERE id=?",
                (rs, row) -> rs.getString("status"),
                bookId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    if (!"ACTIVE".equals(status)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Book is not awaiting review");
    }
  }

  private List<ReviewAsset> assets(UUID bookId) {
    return jdbc.query(
        """
        SELECT a.id, a.asset_type, a.asset_role, a.status AS asset_status,
               v.id AS version_id, v.version_number, v.status AS version_status,
               v.availability_status, v.content_type, v.size_bytes
        FROM catalog.book_asset a
        LEFT JOIN catalog.book_asset_version v ON v.id=a.current_version_id
        WHERE a.book_id=?
        ORDER BY a.asset_type, a.asset_role, a.id
        """,
        (rs, row) ->
            new ReviewAsset(
                rs.getObject("id", UUID.class),
                rs.getString("asset_type"),
                rs.getString("asset_role"),
                rs.getString("asset_status"),
                rs.getObject("version_id", UUID.class),
                rs.getObject("version_number", Integer.class),
                rs.getString("version_status"),
                rs.getString("availability_status"),
                rs.getString("content_type"),
                rs.getObject("size_bytes", Long.class)),
            bookId);
  }

  private static Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  public record ReviewPage(
      List<BookReview> items, int page, int size, long totalItems, boolean last) {}

  public record BookReview(
      UUID id,
      String canonicalTitle,
      String originalTitle,
      String originalLanguage,
      Short firstPublicationYear,
      String description,
      String status,
      Instant updatedAt,
      List<ReviewAsset> assets) {}

  public record ReviewAsset(
      UUID id,
      String assetType,
      String assetRole,
      String status,
      UUID versionId,
      Integer versionNumber,
      String versionStatus,
      String availabilityStatus,
      String contentType,
      Long sizeBytes) {}
}
