package com.bookrush.behaviorservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.Instant;
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
  public BehaviorController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }
  public record Event(@NotBlank String eventKey, @NotBlank String eventType, String issuer,
      String subject, UUID bookId, @NotNull Instant occurredAt, JsonNode payload) {}
  public record Batch(@Valid List<Event> events) {}
  @PostMapping("/events")
  @Transactional
  public ResponseEntity<Map<String,Object>> ingest(@Valid @RequestBody Batch batch, Principal principal) {
    int accepted=0, duplicate=0;
    if (batch.events()==null || batch.events().size()>100) return ResponseEntity.badRequest().body(Map.of("error","batch must contain between 1 and 100 events"));
    String subject = principal == null ? null : principal.getName();
    if (subject == null || subject.isBlank()) return ResponseEntity.status(401).body(Map.of("error","authentication required"));
    for (Event e: batch.events()) {
      String payload = e.payload()==null?"{}":e.payload().toString();
      if (e.eventType().length()>80 || e.eventKey().length()>200 || payload.length()>16_384 || e.occurredAt().isAfter(Instant.now().plusSeconds(300))) { jdbc.update("insert into behavior.rejects(id,event_key,reason,payload) values(?,?,?,?::jsonb)",UUID.randomUUID(),e.eventKey(),"INVALID_EVENT",payload); duplicate++; continue; }
      UUID eventId = UUID.randomUUID();
      int n=jdbc.update("INSERT INTO behavior.events(id,event_key,event_type,identity_issuer,identity_subject,book_id,payload,occurred_at) VALUES (?,?,?,?,?,?,?::jsonb,?) ON CONFLICT (event_key) DO NOTHING",
        eventId,e.eventKey(),e.eventType(),null,subject,e.bookId(),payload,e.occurredAt());
      if(n==1) {
        jdbc.update("INSERT INTO behavior.outbox(id,event_id,event_type,payload,occurred_at) VALUES (?,?,?,?,?)",
          UUID.randomUUID(),eventId,e.eventType(),payload,e.occurredAt());
        accepted++;
      } else duplicate++;
    }
    return ResponseEntity.accepted().body(Map.of("accepted",accepted,"duplicates",duplicate));
  }
  @PostMapping("/aggregates/rebuild")
  public Map<String, Object> rebuild() {
    jdbc.update("""
        INSERT INTO behavior.daily_book(day, book_id, impressions, opens, likes, reads)
        SELECT occurred_at::date, book_id,
          count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION'),
          count(*) FILTER (WHERE event_type = 'BOOK_OPEN'),
          count(*) FILTER (WHERE event_type = 'BOOK_LIKE'),
          count(*) FILTER (WHERE event_type IN ('READ_PROGRESS', 'BOOK_COMPLETE'))
        FROM behavior.events
        WHERE book_id IS NOT NULL
        GROUP BY occurred_at::date, book_id
        ON CONFLICT (day, book_id) DO UPDATE SET
          impressions = excluded.impressions, opens = excluded.opens,
          likes = excluded.likes, reads = excluded.reads
        """);
    jdbc.update("""
        INSERT INTO behavior.daily_user_book
          (day, identity_subject, book_id, impressions, opens, likes, reads, active_seconds)
        SELECT occurred_at::date, identity_subject, book_id,
          count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION'),
          count(*) FILTER (WHERE event_type = 'BOOK_OPEN'),
          count(*) FILTER (WHERE event_type = 'BOOK_LIKE'),
          count(*) FILTER (WHERE event_type IN ('READ_PROGRESS', 'BOOK_COMPLETE')),
          coalesce(sum(CASE
            WHEN event_type = 'READ_SESSION'
              AND payload->>'activeSeconds' ~ '^[0-9]+$'
            THEN (payload->>'activeSeconds')::bigint ELSE 0 END), 0)
        FROM behavior.events
        WHERE book_id IS NOT NULL AND identity_subject IS NOT NULL
        GROUP BY occurred_at::date, identity_subject, book_id
        ON CONFLICT (day, identity_subject, book_id) DO UPDATE SET
          impressions = excluded.impressions, opens = excluded.opens,
          likes = excluded.likes, reads = excluded.reads,
          active_seconds = excluded.active_seconds
        """);
    return Map.of("rebuilt", true);
  }

  @GetMapping("/aggregates")
  public Object aggregates() {
    return jdbc.queryForList("""
        SELECT day, book_id, impressions, opens, likes, reads
        FROM behavior.daily_book ORDER BY day DESC LIMIT 500
        """);
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
}
