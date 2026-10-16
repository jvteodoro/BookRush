package com.bookrush.behaviorservice;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds derived metrics and applies retention. Outbox delivery is owned by the dispatcher. */
@Component
public class BehaviorMaintenance {
  private final JdbcTemplate jdbc;
  private final boolean enabled;
  private final int retentionDays;

  public BehaviorMaintenance(JdbcTemplate jdbc,
      @Value("${bookrush.behavior.maintenance-enabled:true}") boolean enabled,
      @Value("${bookrush.behavior.retention-days:365}") int retentionDays) {
    this.jdbc = jdbc;
    this.enabled = enabled;
    this.retentionDays = Math.max(1, retentionDays);
  }

  @Scheduled(fixedDelayString = "${bookrush.behavior.maintenance-delay-ms:300000}")
  @Transactional
  public void run() {
    if (!enabled) return;
    rebuildAggregates();
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    jdbc.update("DELETE FROM behavior.rejects WHERE received_at < ?", cutoff);
    jdbc.update("DELETE FROM behavior.outbox WHERE published_at IS NOT NULL AND created_at < ?", cutoff);
  }

  private void rebuildAggregates() {
    jdbc.update("""
        INSERT INTO behavior.daily_book(day, book_id, impressions, opens, likes, reads, shares, comments)
        SELECT occurred_at::date, book_id,
          count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION'),
          count(*) FILTER (WHERE event_type = 'BOOK_OPEN'),
          count(*) FILTER (WHERE event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE')),
          count(*) FILTER (WHERE event_type IN ('READ_PROGRESS', 'BOOK_COMPLETE')),
          count(*) FILTER (WHERE event_type = 'BOOK_SHARE'),
          count(*) FILTER (WHERE event_type = 'BOOK_COMMENT')
        FROM behavior.events WHERE book_id IS NOT NULL
        GROUP BY occurred_at::date, book_id
        ON CONFLICT (day, book_id) DO UPDATE SET
          impressions=excluded.impressions, opens=excluded.opens, likes=excluded.likes,
          reads=excluded.reads, shares=excluded.shares, comments=excluded.comments
        """);
    jdbc.update("""
        INSERT INTO behavior.outcome_attribution(id, subject_key, book_id, outcome_type, source_event_id, occurred_at)
        SELECT gen_random_uuid(), identity_subject, book_id, event_type, id, occurred_at
        FROM behavior.events
        WHERE identity_subject IS NOT NULL AND book_id IS NOT NULL
          AND event_type IN ('BOOK_COMPLETE', 'READ_PROGRESS', 'BOOK_LIKE', 'BOOK_SHARE')
        ON CONFLICT (source_event_id, outcome_type) DO NOTHING
        """);
  }

  public Map<String, Object> snapshot() {
    return Map.of(
        "pendingOutbox", jdbc.queryForObject("SELECT count(*) FROM behavior.outbox WHERE published_at IS NULL", Long.class),
        "events", jdbc.queryForObject("SELECT count(*) FROM behavior.events", Long.class),
        "rejects", jdbc.queryForObject("SELECT count(*) FROM behavior.rejects", Long.class));
  }
}
