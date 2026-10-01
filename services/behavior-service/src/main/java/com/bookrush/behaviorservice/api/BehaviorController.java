package com.bookrush.behaviorservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/behavior")
public class BehaviorController {
  private final JdbcTemplate jdbc;
  public BehaviorController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public record Event(@NotBlank String eventKey, @NotBlank String eventType, String issuer,
      String subject, UUID bookId, @NotNull Instant occurredAt, JsonNode payload) {}
  public record Batch(@Valid List<Event> events) {}
  @PostMapping("/events")
  public ResponseEntity<Map<String,Object>> ingest(@Valid @RequestBody Batch batch) {
    int accepted=0, duplicate=0;
    if (batch.events()!=null) for (Event e: batch.events()) {
      int n=jdbc.update("INSERT INTO behavior.events(id,event_key,event_type,identity_issuer,identity_subject,book_id,payload,occurred_at) VALUES (?,?,?,?,?,?,?::jsonb,?) ON CONFLICT (event_key) DO NOTHING",
        UUID.randomUUID(),e.eventKey(),e.eventType(),e.issuer(),e.subject(),e.bookId(),e.payload()==null?"{}":e.payload().toString(),e.occurredAt());
      if(n==1) accepted++; else duplicate++;
    }
    return ResponseEntity.accepted().body(Map.of("accepted",accepted,"duplicates",duplicate));
  }
  @GetMapping("/events/count")
  public Map<String,Object> count(){return Map.of("events",jdbc.queryForObject("select count(*) from behavior.events",Long.class));}
}
