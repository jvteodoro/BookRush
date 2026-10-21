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
    rebuildNow();
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    jdbc.update("DELETE FROM behavior.rejects WHERE received_at < ?", cutoff);
    jdbc.update("DELETE FROM behavior.outbox WHERE published_at IS NOT NULL AND created_at < ?", cutoff);
  }

  @Transactional
  public void rebuildNow() {
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
        INSERT INTO behavior.outcome_attribution(
            id, subject_key, book_id, outcome_type, source_event_id, occurred_at,
            recommendation_item_id, impression_id)
        SELECT gen_random_uuid(), identity_subject, book_id, event_type, id, occurred_at,
          CASE WHEN payload->>'recommendationItemId' ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
            THEN (payload->>'recommendationItemId')::uuid END,
          CASE WHEN payload->>'impressionId' ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
            THEN (payload->>'impressionId')::uuid END
        FROM behavior.events
        WHERE identity_subject IS NOT NULL AND book_id IS NOT NULL
          AND event_type IN ('BOOK_OPEN', 'BOOK_COMPLETE', 'READ_PROGRESS', 'READ_SESSION',
            'BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
        ON CONFLICT (source_event_id, outcome_type) DO UPDATE SET
          recommendation_item_id = excluded.recommendation_item_id,
          impression_id = excluded.impression_id,
          occurred_at = excluded.occurred_at
        """);
    rebuildUserBookSignals();
  }

  private void rebuildUserBookSignals() {
    jdbc.update("DELETE FROM behavior.user_book_signal");
    jdbc.update("""
        WITH scoped AS (
          SELECT id, identity_subject, book_id, event_type, payload, occurred_at
          FROM behavior.events
          WHERE identity_subject IS NOT NULL AND book_id IS NOT NULL
        ), latest_state AS (
          SELECT DISTINCT ON (identity_subject, book_id, event_type)
              identity_subject, book_id, event_type, payload, occurred_at
          FROM scoped
          WHERE event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE', 'LIKE', 'UNLIKE', 'SAVE', 'UNSAVE')
          ORDER BY identity_subject, book_id, event_type, occurred_at DESC, id DESC
        ), aggregate AS (
          SELECT identity_subject, book_id,
              count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION') AS impression_count,
              count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION' AND payload->>'viewable' = 'true') AS viewable_impression_count,
              count(*) FILTER (WHERE event_type = 'BOOK_OPEN') AS open_count,
              count(*) FILTER (WHERE event_type = 'READ_PROGRESS') AS progress_event_count,
              count(*) FILTER (WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completion_count,
              coalesce(sum(CASE
                WHEN event_type = 'READ_SESSION'
                 AND payload->>'activeSeconds' ~ '^[0-9]+$'
                THEN LEAST((payload->>'activeSeconds')::bigint, 86400)
                ELSE 0 END), 0) AS active_seconds,
              coalesce(max(CASE
                WHEN event_type = 'READ_PROGRESS'
                 AND payload->>'percent' ~ '^[0-9]+(\\.[0-9]+)?$'
                THEN LEAST(GREATEST((payload->>'percent')::numeric, 0), 100)
                ELSE 0 END), 0) AS max_progress,
              min(occurred_at) AS first_event_at,
              max(occurred_at) AS last_event_at,
              max(occurred_at) FILTER (WHERE event_type = 'BOOK_OPEN') AS last_opened_at,
              max(occurred_at) FILTER (WHERE event_type = 'READ_PROGRESS') AS last_progress_at,
              max(occurred_at) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION') AS last_impression_at
          FROM scoped
          GROUP BY identity_subject, book_id
        )
        INSERT INTO behavior.user_book_signal(
          identity_subject, book_id, impression_count, viewable_impression_count,
          open_count, progress_event_count, completion_count, active_seconds,
          max_progress, liked, saved, first_event_at, last_event_at,
          last_opened_at, last_progress_at, last_impression_at, updated_at)
        SELECT a.identity_subject, a.book_id, a.impression_count, a.viewable_impression_count,
          a.open_count, a.progress_event_count, a.completion_count, a.active_seconds,
          a.max_progress,
          coalesce((SELECT event_type IN ('BOOK_LIKE', 'LIKE') FROM latest_state s
             WHERE s.identity_subject=a.identity_subject AND s.book_id=a.book_id
               AND s.event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE', 'LIKE', 'UNLIKE')
             ORDER BY s.occurred_at DESC LIMIT 1), false),
          coalesce((SELECT event_type IN ('BOOK_SAVE', 'SAVE') FROM latest_state s
             WHERE s.identity_subject=a.identity_subject AND s.book_id=a.book_id
               AND s.event_type IN ('BOOK_SAVE', 'BOOK_UNSAVE', 'SAVE', 'UNSAVE')
             ORDER BY s.occurred_at DESC LIMIT 1), false),
          a.first_event_at, a.last_event_at, a.last_opened_at, a.last_progress_at,
          a.last_impression_at, now()
        FROM aggregate a
        """);
  }

  public Map<String, Object> snapshot() {
    return Map.of(
        "pendingOutbox", jdbc.queryForObject("SELECT count(*) FROM behavior.outbox WHERE published_at IS NULL", Long.class),
        "events", jdbc.queryForObject("SELECT count(*) FROM behavior.events", Long.class),
        "rejects", jdbc.queryForObject("SELECT count(*) FROM behavior.rejects", Long.class));
  }
}
