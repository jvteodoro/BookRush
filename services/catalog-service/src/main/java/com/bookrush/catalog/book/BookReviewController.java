package com.bookrush.catalog.book;

import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "updatedAt") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
    }
    String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    String pattern = "%" + search + "%";
    String predicate = "status='REVIEW_REQUIRED' AND (?='' OR lower(coalesce(canonical_title,'') || ' ' || coalesce(original_title,'') || ' ' || coalesce(description,'')) LIKE ?)";
    Long totalResult =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog.book WHERE " + predicate,
            Long.class,
            search,
            pattern);
    long total = totalResult == null ? 0L : totalResult;
    int offset = Math.multiplyExact(page, size);
    String listSql = String.format(
        "SELECT id, canonical_title, original_title, original_language, "
            + "first_publication_year, description, status, updated_at "
            + "FROM catalog.book WHERE %s ORDER BY %s %s, id LIMIT ? OFFSET ?",
        predicate, orderColumn(sort), orderDirection(direction));
    List<BookReview> items =
        jdbc.query(
            listSql,
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
            search,
            pattern,
            size,
            offset);
    return new ReviewPage(items, page, size, total, offset + items.size() >= total);
  }

  private static String orderColumn(String sort) {
    return switch (sort == null ? "" : sort) {
      case "title" -> "canonical_title";
      case "language" -> "original_language";
      case "year" -> "first_publication_year";
      default -> "updated_at";
    };
  }

  private static String orderDirection(String direction) {
    return "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
  }

  @GetMapping("/approval-history")
  public ApprovalHistoryPage approvalHistory(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    validatePage(page, size);
    Long totalResult =
        jdbc.queryForObject("SELECT count(*) FROM catalog.book_review_audit", Long.class);
    long total = totalResult == null ? 0L : totalResult;
    int offset = Math.multiplyExact(page, size);
    List<ApprovalHistory> items =
        jdbc.query(
            """
            SELECT h.id, h.book_id, b.canonical_title, b.original_title, h.action,
                   h.previous_status, h.new_status, h.actor_subject, h.reason,
                   h.created_at, b.status AS current_status
            FROM catalog.book_review_audit h
            JOIN catalog.book b ON b.id=h.book_id
            ORDER BY h.created_at DESC, h.id DESC
            LIMIT ? OFFSET ?
            """,
            (rs, row) ->
                new ApprovalHistory(
                    rs.getObject("id", UUID.class),
                    rs.getObject("book_id", UUID.class),
                    rs.getString("canonical_title"),
                    rs.getString("original_title"),
                    rs.getString("action"),
                    rs.getString("previous_status"),
                    rs.getString("new_status"),
                    rs.getString("actor_subject"),
                    rs.getString("reason"),
                    toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                    rs.getString("current_status")),
            size,
            offset);
    return new ApprovalHistoryPage(items, page, size, total, offset + items.size() >= total);
  }

  @PostMapping("/{bookId}/approve")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void approve(@PathVariable UUID bookId, Principal principal) {
    int changed =
        jdbc.update(
            "UPDATE catalog.book SET status='ACTIVE' WHERE id=? AND status='REVIEW_REQUIRED'",
            bookId);
    if (changed > 0) {
      audit(bookId, "APPROVED", "REVIEW_REQUIRED", "ACTIVE", principal);
      return;
    }
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

  @PostMapping("/{bookId}/revoke-approval")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void revokeApproval(@PathVariable UUID bookId, Principal principal) {
    int changed =
        jdbc.update(
            """
            UPDATE catalog.book SET status='REVIEW_REQUIRED'
            WHERE id=? AND status='ACTIVE'
              AND EXISTS (
                SELECT 1 FROM catalog.book_review_audit h
                WHERE h.book_id=? AND h.action='APPROVED')
            """,
            bookId,
            bookId);
    if (changed > 0) {
      audit(bookId, "REVOKED", "ACTIVE", "REVIEW_REQUIRED", principal);
      return;
    }
    String status =
        jdbc.query(
                "SELECT status FROM catalog.book WHERE id=?",
                (rs, row) -> rs.getString("status"),
                bookId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    if ("REVIEW_REQUIRED".equals(status)) return;
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Book has no active review approval");
  }

  private void audit(
      UUID bookId, String action, String previousStatus, String newStatus, Principal principal) {
    String actor = principal == null ? null : principal.getName();
    if (actor == null || actor.isBlank()) actor = "unknown";
    jdbc.update(
        """
        INSERT INTO catalog.book_review_audit
          (id, book_id, action, previous_status, new_status, actor_subject)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        bookId,
        action,
        previousStatus,
        newStatus,
        actor);
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
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

  public record ApprovalHistoryPage(
      List<ApprovalHistory> items, int page, int size, long totalItems, boolean last) {}

  public record ApprovalHistory(
      UUID id,
      UUID bookId,
      String canonicalTitle,
      String originalTitle,
      String action,
      String previousStatus,
      String newStatus,
      String actorSubject,
      String reason,
      Instant createdAt,
      String currentStatus) {}

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
