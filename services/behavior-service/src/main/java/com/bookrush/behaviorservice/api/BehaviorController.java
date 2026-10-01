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
      if (e.eventType().length()>80 || e.eventKey().length()>200 || e.occurredAt().isAfter(Instant.now().plusSeconds(300))) { jdbc.update("insert into behavior.rejects(id,event_key,reason,payload) values(?,?,?,?::jsonb)",UUID.randomUUID(),e.eventKey(),"INVALID_EVENT",e.payload()==null?"{}":e.payload().toString()); duplicate++; continue; }
      int n=jdbc.update("INSERT INTO behavior.events(id,event_key,event_type,identity_issuer,identity_subject,book_id,payload,occurred_at) VALUES (?,?,?,?,?,?,?::jsonb,?) ON CONFLICT (event_key) DO NOTHING",
        UUID.randomUUID(),e.eventKey(),e.eventType(),e.issuer(),e.subject(),e.bookId(),e.payload()==null?"{}":e.payload().toString(),e.occurredAt());
      if(n==1) accepted++; else duplicate++;
    }
    return ResponseEntity.accepted().body(Map.of("accepted",accepted,"duplicates",duplicate));
  }
  @PostMapping("/aggregates/rebuild") public Map<String,Object> rebuild(){jdbc.update("insert into behavior.daily_book(day,book_id,impressions,opens,likes,reads) select occurred_at::date,book_id,count(*) filter(where event_type='EXCERPT_IMPRESSION'),count(*) filter(where event_type='BOOK_OPEN'),count(*) filter(where event_type='BOOK_LIKE'),count(*) filter(where event_type in ('READ_PROGRESS','BOOK_COMPLETE')) from behavior.events where book_id is not null group by occurred_at::date,book_id on conflict(day,book_id) do update set impressions=excluded.impressions,opens=excluded.opens,likes=excluded.likes,reads=excluded.reads");return Map.of("rebuilt",true);}
 @GetMapping("/aggregates") public Object aggregates(){return jdbc.queryForList("select day,book_id,impressions,opens,likes,reads from behavior.daily_book order by day desc limit 500");}
 @GetMapping("/events/count")
  public Map<String,Object> count(){return Map.of("events",jdbc.queryForObject("select count(*) from behavior.events",Long.class));}
}
