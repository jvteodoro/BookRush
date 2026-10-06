package com.bookrush.readerstateservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reader")
public class ReaderStateController {
  private final JdbcTemplate jdbc;

  public ReaderStateController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  private int integerValue(Map<String, Object> body, String key) {
    Object value = body.get(key);
    return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
  }

  public record Progress(@NotNull Long positionCodepoint, @NotNull Double percent) {}

  @PostMapping("/books/{bookId}/opened")
  public Map<String, Object> opened(@PathVariable UUID bookId, Principal principal) {
    jdbc.update("insert into reader_state.recent(subject_key,book_id) values(?,?) "
        + "on conflict(subject_key,book_id) do update set last_opened_at=now()",
        subject(principal), bookId);
    return Map.of("bookId", bookId, "recorded", true);
  }

  @GetMapping("/recent")
  public Object recent(Principal principal) {
    return jdbc.queryForList("SELECT book_id, last_opened_at FROM reader_state.recent "
        + "WHERE subject_key = ? ORDER BY last_opened_at DESC LIMIT 50", subject(principal));
  }

  @PostMapping("/sessions")
  public Object startSession(Principal principal) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO reader_state.session(id, subject_key, started_at) VALUES (?, ?, now())",
        id, subject(principal));
    return Map.of("id", id, "startedAt", Instant.now());
  }

  @PostMapping("/sessions/{id}/end")
  public Object endSession(@PathVariable UUID id, @RequestBody Map<String, Object> body,
      Principal principal) {
    String subject = subject(principal);
    int seconds = integerValue(body, "activeSeconds");
    int updated = jdbc.update("UPDATE reader_state.session SET ended_at = now(), active_seconds = ? "
        + "WHERE id = ? AND subject_key = ? AND ended_at IS NULL", seconds, id, subject);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
        "reading session not found");
    jdbc.update("INSERT INTO reader_state.reading_day(subject_key, day, minutes) VALUES (?, CURRENT_DATE, ?) "
        + "ON CONFLICT (subject_key, day) DO UPDATE SET minutes = "
        + "reader_state.reading_day.minutes + EXCLUDED.minutes", subject, seconds / 60);
    return Map.of("id", id, "activeSeconds", seconds);
  }

  @GetMapping("/streak")
  public Map<String, Object> streak(Principal principal) {
    String subject = subject(principal);
    var days = jdbc.queryForList("SELECT day FROM reader_state.reading_day WHERE subject_key = ? "
        + "AND minutes > 0 ORDER BY day DESC LIMIT 366", subject);
    int current = 0;
    LocalDate expected = LocalDate.now();
    for (var row : days) {
      LocalDate day = ((java.sql.Date) row.get("day")).toLocalDate();
      if (day.equals(expected)) { current++; expected = expected.minusDays(1); }
      else if (day.isBefore(expected)) break;
    }
    return Map.of("currentDays", current, "measuredUntil", LocalDate.now());
  }

  @GetMapping("/library")
  public Object library(Principal principal) {
    return jdbc.queryForList("SELECT book_id, status, created_at FROM reader_state.library "
        + "WHERE subject_key = ? ORDER BY created_at DESC", subject(principal));
  }

  @PutMapping("/library/{bookId}")
  public Map<String, Object> save(@PathVariable UUID bookId, Principal principal) {
    jdbc.update("INSERT INTO reader_state.library(subject_key, book_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        subject(principal), bookId);
    return Map.of("bookId", bookId, "saved", true);
  }

  @DeleteMapping("/library/{bookId}")
  public void remove(@PathVariable UUID bookId, Principal principal) {
    jdbc.update("DELETE FROM reader_state.library WHERE subject_key = ? AND book_id = ?",
        subject(principal), bookId);
  }

  @PutMapping("/books/{bookId}/progress")
  public Map<String, Object> progress(@PathVariable UUID bookId, @Valid @RequestBody Progress value,
      Principal principal) {
    jdbc.update("INSERT INTO reader_state.progress(subject_key, book_id, position_codepoint, percent) "
        + "VALUES (?, ?, ?, ?) ON CONFLICT (subject_key, book_id) DO UPDATE SET "
        + "position_codepoint = EXCLUDED.position_codepoint, percent = EXCLUDED.percent, updated_at = now()",
        subject(principal), bookId, value.positionCodepoint(), value.percent());
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
