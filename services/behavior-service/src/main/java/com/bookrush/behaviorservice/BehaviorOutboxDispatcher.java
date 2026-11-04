package com.bookrush.behaviorservice;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong published24h = new AtomicLong();
  private final AtomicLong failedAttempts = new AtomicLong();
  private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

  public BehaviorOutboxDispatcher(JdbcTemplate jdbc,
      MeterRegistry metrics,
      @Value("${bookrush.behavior.outbox-sink-url:}") String sinkUrl,
      @Value("${bookrush.behavior.outbox-sink-token:}") String sinkToken,
      @Value("${bookrush.behavior.outbox-batch-size:100}") int batchSize) {
    this.jdbc = jdbc;
    this.http = RestClient.builder().build();
    this.sinkUrl = sinkUrl == null ? "" : sinkUrl.trim();
    this.sinkToken = sinkToken == null ? "" : sinkToken.trim();
    this.batchSize = Math.max(1, batchSize);
    Gauge.builder("bookrush.behavior.outbox.pending", pending, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.outbox.published_24h", published24h, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.outbox.failed_attempts", failedAttempts, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.outbox.oldest_pending_age_seconds", oldestPendingAgeSeconds, AtomicLong::get)
        .register(metrics);
  }

  @Scheduled(fixedDelayString = "${bookrush.behavior.outbox-delay-ms:5000}")
  public void dispatch() {
    refreshMetrics();
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
    refreshMetrics();
  }

  private void refreshMetrics() {
    pending.set(value("SELECT count(*) FROM behavior.outbox WHERE published_at IS NULL"));
    published24h.set(value("SELECT count(*) FROM behavior.outbox WHERE published_at >= now() - INTERVAL '24 hours'"));
    failedAttempts.set(value("SELECT coalesce(sum(attempts), 0) FROM behavior.outbox WHERE attempts > 0"));
    Long age = jdbc.queryForObject("SELECT coalesce(extract(epoch FROM now() - min(created_at)), 0)::bigint "
        + "FROM behavior.outbox WHERE published_at IS NULL", Long.class);
    oldestPendingAgeSeconds.set(age == null ? 0 : Math.max(0, age));
  }

  private long value(String sql) {
    Long result = jdbc.queryForObject(sql, Long.class);
    return result == null ? 0 : Math.max(0, result);
  }
}
