package com.bookrush.behaviorservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.bookrush.behaviorservice.BehaviorMaintenance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/behavior")
public class BehaviorController {
  private final JdbcTemplate jdbc;
  private final BehaviorMaintenance maintenance;
  public BehaviorController(JdbcTemplate jdbc, BehaviorMaintenance maintenance) {
    this.jdbc = jdbc;
    this.maintenance = maintenance;
  }
  public record Event(@NotBlank String eventKey, @NotBlank String eventType, String issuer,
      String subject, UUID bookId, @NotNull Instant occurredAt, JsonNode payload) {}
  public record Batch(@Valid List<Event> events) {}

  private UUID correlationUuid(JsonNode payload, String field) {
    if (payload == null || !payload.has(field) || payload.get(field).isNull()) return null;
    JsonNode value = payload.get(field);
    if (!value.isTextual()) throw new IllegalArgumentException(field + " must be a UUID string");
    try {
      return UUID.fromString(value.asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(field + " must be a UUID string", exception);
    }
  }
  @PostMapping("/events")
  @Transactional
  public ResponseEntity<Map<String,Object>> ingest(@Valid @RequestBody Batch batch, Principal principal) {
    int accepted=0, duplicate=0;
    if (batch.events()==null || batch.events().size()>100) return ResponseEntity.badRequest().body(Map.of("error","batch must contain between 1 and 100 events"));
    String subject = principal == null ? null : principal.getName();
    if (subject == null || subject.isBlank()) return ResponseEntity.status(401).body(Map.of("error","authentication required"));
    for (Event e: batch.events()) {
      String payload = e.payload()==null?"{}":e.payload().toString();
      try {
        correlationUuid(e.payload(), "recommendationItemId");
        correlationUuid(e.payload(), "impressionId");
      } catch (IllegalArgumentException exception) {
        jdbc.update("insert into behavior.rejects(id,event_key,reason,payload) values(?,?,?,?::jsonb)",UUID.randomUUID(),e.eventKey(),"INVALID_CORRELATION",payload);
        duplicate++;
        continue;
      }
      if (e.eventType().length()>80 || e.eventKey().length()>200 || payload.length()>16_384 || e.occurredAt().isAfter(Instant.now().plusSeconds(300))) { jdbc.update("insert into behavior.rejects(id,event_key,reason,payload) values(?,?,?,?::jsonb)",UUID.randomUUID(),e.eventKey(),"INVALID_EVENT",payload); duplicate++; continue; }
      UUID eventId = UUID.randomUUID();
      int n=jdbc.update("INSERT INTO behavior.events(id,event_key,event_type,identity_issuer,identity_subject,book_id,payload,occurred_at) VALUES (?,?,?,?,?,?,?::jsonb,?) ON CONFLICT (event_key) DO NOTHING",
        eventId,e.eventKey(),e.eventType(),null,subject,e.bookId(),payload,Timestamp.from(e.occurredAt()));
      if(n==1) {
        jdbc.update("INSERT INTO behavior.outbox(id,event_id,event_type,payload,occurred_at) VALUES (?,?,?,?::jsonb,?)",
          UUID.randomUUID(),eventId,e.eventType(),payload,Timestamp.from(e.occurredAt()));
        accepted++;
      } else duplicate++;
    }
    return ResponseEntity.accepted().body(Map.of("accepted",accepted,"duplicates",duplicate));
  }
  @PostMapping("/aggregates/rebuild")
  public Map<String, Object> rebuild() {
    maintenance.rebuildNow();
    return Map.of("rebuilt", true);
  }


  @GetMapping("/aggregates")
  public Object aggregates() {
    return jdbc.queryForList("""
        SELECT day, book_id, impressions, opens, likes, reads, shares, comments
        FROM behavior.daily_book ORDER BY day DESC LIMIT 500
        """);
  }
  @GetMapping("/aggregates/books/{bookId}")
  public Object bookAggregates(@PathVariable UUID bookId) {
    return jdbc.queryForList("SELECT day, book_id, impressions, opens, likes, reads, shares, comments FROM behavior.daily_book WHERE book_id=? ORDER BY day DESC LIMIT 365", bookId);
  }

  @GetMapping("/aggregates/dashboard")
  public Map<String, Object> dashboardAggregates(
      @RequestParam(defaultValue = "30") int days) {
    int window = Math.max(1, Math.min(days, 365));
    var rows = jdbc.queryForList("""
        SELECT day, sum(impressions) impressions, sum(opens) opens, sum(likes) likes,
          sum(reads) reads, sum(shares) shares, sum(comments) comments
        FROM behavior.daily_book
        WHERE day >= current_date - (? - 1)
        GROUP BY day ORDER BY day DESC
        """, window);
    return Map.of("days", window, "rows", rows);
  }

  @GetMapping("/aggregates/me")
  public Object myAggregates(Principal principal) {
    if (principal == null) {
      return ResponseEntity.status(401).body(Map.of("error", "authentication required"));
    }
    return jdbc.queryForList("""
        SELECT day, book_id, impressions, opens, likes, reads, active_seconds
        FROM behavior.daily_user_book
        WHERE identity_subject = ? ORDER BY day DESC LIMIT 500
        """, principal.getName());
  }
 @GetMapping("/events/count")
  public Map<String, Object> count() {
    return Map.of("events", jdbc.queryForObject("select count(*) from behavior.events", Long.class));
  }
  @GetMapping("/operations/metrics")
  public Map<String, Object> operationsMetrics() {
    return maintenance.snapshot();
  }
}
