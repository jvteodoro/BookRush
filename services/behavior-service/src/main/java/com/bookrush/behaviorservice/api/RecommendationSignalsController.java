package com.bookrush.behaviorservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Least-privilege, bounded read model for recommendation serving. */
@RestController
@RequestMapping("/api/internal/v1/recommendation")
public class RecommendationSignalsController {
  private final JdbcTemplate jdbc;

  public RecommendationSignalsController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record SignalsRequest(@NotBlank String subject, List<UUID> bookIds,
      Integer limit, Integer offset) {}

  public record UserBookSignal(UUID bookId, long impressionCount,
      long viewableImpressionCount, long openCount, long progressEventCount,
      long completionCount, long activeSeconds, java.math.BigDecimal maxProgress,
      boolean liked, boolean saved, Instant firstEventAt, Instant lastEventAt,
      Instant lastOpenedAt, Instant lastProgressAt, Instant lastImpressionAt) {}

  public record SignalsResponse(int limit, int offset, long total, boolean hasMore,
      List<UserBookSignal> items) {}

  @PostMapping("/signals")
  public ResponseEntity<?> signals(@Valid @RequestBody SignalsRequest request) {
    String subject = request.subject().trim();
    if (subject.isBlank() || subject.length() > 255) {
      return ResponseEntity.badRequest().body(Map.of("error", "subject must contain 1 to 255 characters"));
    }
    int limit = request.limit() == null ? 100 : request.limit();
    int offset = request.offset() == null ? 0 : request.offset();
    if (limit < 1 || limit > 100 || offset < 0 || offset > 10_000) {
      return ResponseEntity.badRequest().body(Map.of("error", "limit must be 1..100 and offset must be 0..10000"));
    }
    List<UUID> bookIds = request.bookIds() == null
        ? List.of() : List.copyOf(new LinkedHashSet<>(request.bookIds()));
    if (bookIds.stream().anyMatch(java.util.Objects::isNull)) {
      return ResponseEntity.badRequest().body(Map.of("error", "bookIds must contain UUID values"));
    }
    if (bookIds.size() > 100) {
      return ResponseEntity.badRequest().body(Map.of("error", "bookIds must contain at most 100 values"));
    }
    String filter = bookIds.isEmpty() ? "" : " AND book_id IN (" + placeholders(bookIds.size()) + ")";
    Object[] filterArgs = bookIds.toArray();
    Object[] queryArgs = new Object[1 + filterArgs.length + 2];
    queryArgs[0] = subject;
    System.arraycopy(filterArgs, 0, queryArgs, 1, filterArgs.length);
    queryArgs[queryArgs.length - 2] = limit;
    queryArgs[queryArgs.length - 1] = offset;
    String select = """
        SELECT book_id, impression_count, viewable_impression_count, open_count,
          progress_event_count, completion_count, active_seconds, max_progress,
          liked, saved, first_event_at, last_event_at, last_opened_at,
          last_progress_at, last_impression_at
        FROM behavior.user_book_signal
        WHERE identity_subject = ?
        """ + filter + " ORDER BY last_event_at DESC, book_id LIMIT ? OFFSET ?";
    List<UserBookSignal> items = jdbc.query(select, queryArgs, (rs, rowNum) -> new UserBookSignal(
        rs.getObject("book_id", UUID.class), rs.getLong("impression_count"),
        rs.getLong("viewable_impression_count"), rs.getLong("open_count"),
        rs.getLong("progress_event_count"), rs.getLong("completion_count"),
        rs.getLong("active_seconds"), rs.getBigDecimal("max_progress"),
        rs.getBoolean("liked"), rs.getBoolean("saved"),
        timestamp(rs.getTimestamp("first_event_at")), timestamp(rs.getTimestamp("last_event_at")),
        timestamp(rs.getTimestamp("last_opened_at")), timestamp(rs.getTimestamp("last_progress_at")),
        timestamp(rs.getTimestamp("last_impression_at"))));
    String count = "SELECT count(*) FROM behavior.user_book_signal WHERE identity_subject = ?" + filter;
    Object[] countArgs = new Object[1 + filterArgs.length];
    countArgs[0] = subject;
    System.arraycopy(filterArgs, 0, countArgs, 1, filterArgs.length);
    long total = jdbc.queryForObject(count, countArgs, Long.class);
    return ResponseEntity.ok(new SignalsResponse(limit, offset, total, offset + items.size() < total, items));
  }

  private static String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }

  private static Instant timestamp(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
