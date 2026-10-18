package com.bookrush.behaviorservice;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Delivers the durable outbox to an explicitly configured internal sink. */
@Component
public class BehaviorOutboxDispatcher {
  private final JdbcTemplate jdbc;
  private final RestClient http;
  private final String sinkUrl;
  private final String sinkToken;
  private final int batchSize;

  public BehaviorOutboxDispatcher(JdbcTemplate jdbc,
      @Value("${bookrush.behavior.outbox-sink-url:}") String sinkUrl,
      @Value("${bookrush.behavior.outbox-sink-token:}") String sinkToken,
      @Value("${bookrush.behavior.outbox-batch-size:100}") int batchSize) {
    this.jdbc = jdbc;
    this.http = RestClient.builder().build();
    this.sinkUrl = sinkUrl == null ? "" : sinkUrl.trim();
    this.sinkToken = sinkToken == null ? "" : sinkToken.trim();
    this.batchSize = Math.max(1, batchSize);
  }

  @Scheduled(fixedDelayString = "${bookrush.behavior.outbox-delay-ms:5000}")
  public void dispatch() {
    if (sinkUrl.isBlank()) return;
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, event_id, event_type, payload, occurred_at FROM behavior.outbox "
            + "WHERE published_at IS NULL ORDER BY created_at LIMIT ?", batchSize);
    for (Map<String, Object> row : rows) {
      UUID id = (UUID) row.get("id");
      try {
        http.post().uri(sinkUrl).headers(headers -> {
          if (!sinkToken.isBlank()) headers.set("X-Behavior-Outbox-Token", sinkToken);
        }).body(row).retrieve().toBodilessEntity();
        jdbc.update("UPDATE behavior.outbox SET published_at=now(), attempts=attempts+1, last_error=NULL WHERE id=?", id);
      } catch (RuntimeException error) {
        jdbc.update("UPDATE behavior.outbox SET attempts=attempts+1, last_error=? WHERE id=?",
            error.getClass().getSimpleName() + ": delivery failed", id);
      }
    }
  }
}
