package com.bookrush.readerstateservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import com.bookrush.readerstateservice.BehaviorEventPublisher;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reader")
public class ReaderStateController {
  private final JdbcTemplate jdbc;
  private final BehaviorEventPublisher behavior;
  private final DailyPlantService dailyPlant;

  public ReaderStateController(JdbcTemplate jdbc, BehaviorEventPublisher behavior, DailyPlantService dailyPlant) {
    this.jdbc = jdbc;
    this.behavior = behavior;
    this.dailyPlant = dailyPlant;
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  private String authorization(HttpServletRequest request) {
    return request == null ? null : request.getHeader("Authorization");
  }

  private Map<String, Object> correlation(HttpServletRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : Map.of(
        "recommendationItemId", "X-BookRush-Recommendation-Item",
        "impressionId", "X-BookRush-Impression").entrySet()) {
      String value = request == null ? null : request.getHeader(entry.getValue());
      if (value == null || value.isBlank()) continue;
      try {
        payload.put(entry.getKey(), UUID.fromString(value));
      } catch (IllegalArgumentException ignored) {
        // Correlation is optional; malformed client metadata must not affect reading.
      }
    }
    return payload;
  }

  private int integerValue(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
  }

  public record Progress(@NotNull Long positionCodepoint, @NotNull Double percent) {}

  @Transactional
  @PostMapping("/books/{bookId}/opened")
  public Map<String, Object> opened(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    String subject = subject(principal);
    jdbc.update("insert into reader_state.recent(subject_key,book_id) values(?,?) "
        + "on conflict(subject_key,book_id) do update set last_opened_at=now()",
        subject, bookId);
    jdbc.update("INSERT INTO reader_state.seen_book(subject_key,book_id) VALUES (?,?) "
        + "ON CONFLICT(subject_key,book_id) DO UPDATE SET last_seen_at=clock_timestamp()",
        subject, bookId);
    behavior.publish("BOOK_OPEN", bookId, correlation(request), authorization(request));
    return Map.of("bookId", bookId, "recorded", true);
  }

  @GetMapping("/books/seen")
  public Map<String, Object> seen(@RequestParam java.util.List<UUID> bookIds, Principal principal) {
    String subject = subject(principal);
    if (bookIds.isEmpty() || bookIds.size() > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookIds must contain 1 to 100 UUIDs");
    }
    var entries = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(
        "SELECT book_id, first_seen_at, last_seen_at FROM reader_state.seen_book "
            + "WHERE subject_key=:subject AND book_id IN (:books) ORDER BY book_id",
        Map.of("subject", subject, "books", bookIds.stream().distinct().toList()),
        (row, index) -> Map.of(
            "bookId", row.getObject("book_id", UUID.class),
            "firstSeenAt", row.getObject("first_seen_at", java.time.OffsetDateTime.class).toInstant(),
            "lastSeenAt", row.getObject("last_seen_at", java.time.OffsetDateTime.class).toInstant()));
    return Map.of("items", entries);
  }

  @GetMapping("/recent")
  public Object recent(Principal principal) {
    return jdbc.queryForList("SELECT book_id, last_opened_at FROM reader_state.recent "
        + "WHERE subject_key = ? ORDER BY last_opened_at DESC LIMIT 50", subject(principal));
  }

  @PostMapping("/sessions")
  public Object startSession(@RequestBody(required = false) Map<String, Object> body,
      Principal principal, HttpServletRequest request) {
    UUID id = UUID.randomUUID();
    UUID bookId = uuidValue(body == null ? null : body.get("bookId"));
    Map<String, Object> origin = correlation(request);
    jdbc.update("INSERT INTO reader_state.session(id, subject_key, book_id, recommendation_item_id, impression_id, started_at) "
        + "VALUES (?, ?, ?, ?, ?, now())", id, subject(principal), bookId,
        origin.get("recommendationItemId"), origin.get("impressionId"));
    return Map.of("id", id, "startedAt", Instant.now());
  }

  @Transactional
  @PostMapping("/sessions/{id}/end")
  public Object endSession(@PathVariable UUID id, @RequestBody Map<String, Object> body,
      Principal principal, HttpServletRequest request) {
    String subject = subject(principal);
    int seconds = integerValue(body, "activeSeconds");
    Map<String, Object> sessionOrigin = jdbc.query("SELECT book_id, recommendation_item_id, impression_id "
        + "FROM reader_state.session WHERE id = ? AND subject_key = ? AND ended_at IS NULL",
        rs -> {
          if (!rs.next()) return Map.of();
          Map<String, Object> value = new LinkedHashMap<>();
          value.put("bookId", rs.getObject("book_id", UUID.class));
          value.put("recommendationItemId", rs.getObject("recommendation_item_id", UUID.class));
          value.put("impressionId", rs.getObject("impression_id", UUID.class));
          return value;
        },
        id, subject);
    UUID bookId = (UUID) sessionOrigin.get("bookId");
    int updated = jdbc.update("UPDATE reader_state.session SET ended_at = now(), active_seconds = ? "
        + "WHERE id = ? AND subject_key = ? AND ended_at IS NULL", seconds, id, subject);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
        "reading session not found");
    jdbc.update("INSERT INTO reader_state.reading_day(subject_key, day, minutes) VALUES (?, CURRENT_DATE, ?) "
        + "ON CONFLICT (subject_key, day) DO UPDATE SET minutes = "
        + "reader_state.reading_day.minutes + EXCLUDED.minutes", subject, seconds / 60);
    Map<String, Object> payload = new LinkedHashMap<>(correlation(request));
    if (!payload.containsKey("recommendationItemId") && sessionOrigin.get("recommendationItemId") != null) {
      payload.put("recommendationItemId", sessionOrigin.get("recommendationItemId"));
    }
    if (!payload.containsKey("impressionId") && sessionOrigin.get("impressionId") != null) {
      payload.put("impressionId", sessionOrigin.get("impressionId"));
    }
    payload.put("activeSeconds", seconds);
    behavior.publish("READ_SESSION", bookId, payload, authorization(request));
    return Map.of("id", id, "activeSeconds", seconds, "plant", dailyPlant.get(subject));
  }

  @GetMapping("/streak")
  public Map<String, Object> streak(Principal principal) {
    String subject = subject(principal);
    var plant = dailyPlant.get(subject);
    return Map.of("currentDays", plant.get("streakDays"), "measuredUntil", plant.get("measuredUntil"));
  }

  @GetMapping("/plant")
  public Map<String, Object> plant(Principal principal) {
    return dailyPlant.get(subject(principal));
  }

  public record ReadingInterval(@NotNull UUID eventId, @NotNull Integer activeSeconds, @NotNull Instant endedAt) {}

  @PostMapping("/plant")
  public Map<String, Object> readingTime(@Valid @RequestBody ReadingInterval interval, Principal principal) {
    return dailyPlant.record(subject(principal), interval.eventId(), interval.activeSeconds(), interval.endedAt());
  }

  private UUID uuidValue(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    try { return UUID.fromString(String.valueOf(value)); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  @GetMapping("/library")
  public Object library(Principal principal) {
    return jdbc.queryForList("SELECT book_id, status, created_at FROM reader_state.library "
        + "WHERE subject_key = ? ORDER BY created_at DESC", subject(principal));
  }

  @PutMapping("/library/{bookId}")
  public Map<String, Object> save(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    jdbc.update("INSERT INTO reader_state.library(subject_key, book_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        subject(principal), bookId);
    behavior.publish("BOOK_SAVE", bookId, correlation(request), authorization(request));
    return Map.of("bookId", bookId, "saved", true);
  }

  @DeleteMapping("/library/{bookId}")
  public void remove(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    jdbc.update("DELETE FROM reader_state.library WHERE subject_key = ? AND book_id = ?",
        subject(principal), bookId);
    behavior.publish("BOOK_UNSAVE", bookId, correlation(request), authorization(request));
  }

  @PutMapping("/books/{bookId}/progress")
  public Map<String, Object> progress(@PathVariable UUID bookId, @Valid @RequestBody Progress value,
      Principal principal, HttpServletRequest request) {
    String subject = subject(principal);
    jdbc.update("INSERT INTO reader_state.progress(subject_key, book_id, position_codepoint, percent) "
        + "VALUES (?, ?, ?, ?) ON CONFLICT (subject_key, book_id) DO UPDATE SET "
        + "position_codepoint = EXCLUDED.position_codepoint, percent = EXCLUDED.percent, updated_at = now()",
        subject, bookId, value.positionCodepoint(), value.percent());
    Map<String, Object> payload = new LinkedHashMap<>(correlation(request));
    payload.put("percent", value.percent());
    behavior.publish("READ_PROGRESS", bookId, payload, authorization(request));
    if (value.percent() >= 100) {
      Map<String, Object> completion = new LinkedHashMap<>(correlation(request));
      completion.put("percent", 100);
      behavior.publishWithKey("BOOK_COMPLETE", bookId, completion,
          authorization(request), "BOOK_COMPLETE:" + subject + ":" + bookId);
    }
    return Map.of("bookId", bookId, "percent", value.percent());
  }

  @PostMapping("/books/{bookId}/bookmarks")
  public Map<String, Object> bookmark(@PathVariable UUID bookId, @RequestBody Map<String, Object> body,
      Principal principal) {
    long position = integerValue(body, "positionCodepoint");
    String note = String.valueOf(body.getOrDefault("note", ""));
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO reader_state.bookmarks(id, subject_key, book_id, position_codepoint, note) "
        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (subject_key, book_id, position_codepoint) "
        + "DO UPDATE SET note = EXCLUDED.note", id, subject(principal), bookId, position, note);
    return Map.of("id", id, "bookId", bookId, "positionCodepoint", position, "note", note);
  }

  @GetMapping("/books/{bookId}/bookmarks")
  public Object bookmarks(@PathVariable UUID bookId, Principal principal) {
    return jdbc.queryForList("SELECT id, position_codepoint, note, created_at FROM reader_state.bookmarks "
        + "WHERE subject_key = ? AND book_id = ? ORDER BY position_codepoint", subject(principal), bookId);
  }

  @GetMapping("/books/{bookId}/progress")
  public Object getProgress(@PathVariable UUID bookId, Principal principal) {
    return jdbc.queryForList("SELECT position_codepoint, percent, updated_at FROM reader_state.progress "
        + "WHERE subject_key = ? AND book_id = ?", subject(principal), bookId);
  }
}
