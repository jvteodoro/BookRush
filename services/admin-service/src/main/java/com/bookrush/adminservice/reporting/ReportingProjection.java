package com.bookrush.adminservice.reporting;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds bounded, rebuildable reporting facts from the behavior export boundary. */
@Component
public class ReportingProjection {
  private static final Logger LOG = LoggerFactory.getLogger(ReportingProjection.class);
  private static final String VERSION = "events-v1-untrusted-population";
  private final JdbcTemplate jdbc;

  public ReportingProjection(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(fixedDelayString = "${bookrush.reporting.refresh-delay-ms:300000}")
  @Transactional
  public void refresh() {
    Instant refreshed = Instant.now();
    try {
      jdbc.update("""
          INSERT INTO reporting.reader_day(
              day, population, platform, active_users, newly_active_users, sessions,
              active_seconds, book_opens, meaningful_reads, completions,
              calculation_version, refreshed_at, source_watermark)
          SELECT occurred_at::date, 'UNCLASSIFIED', 'UNKNOWN',
              count(DISTINCT identity_subject) FILTER (WHERE identity_subject IS NOT NULL),
              NULL,
              count(*) FILTER (WHERE event_type = 'READ_SESSION'),
              coalesce(sum(CASE WHEN event_type = 'READ_SESSION'
                  AND payload->>'activeSeconds' ~ '^[0-9]+$'
                THEN LEAST((payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0),
              count(*) FILTER (WHERE event_type = 'BOOK_OPEN'),
              count(DISTINCT (identity_subject, book_id)) FILTER (
                WHERE event_type = 'READ_SESSION'
                  AND payload->>'activeSeconds' ~ '^[0-9]+$'
                  AND (payload->>'activeSeconds')::bigint >= 60),
              count(DISTINCT (identity_subject, book_id)) FILTER (
                WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')),
              ?, ?, max(occurred_at)
          FROM behavior.events
          WHERE occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
          ON CONFLICT (day, population, platform) DO UPDATE SET
              active_users = EXCLUDED.active_users,
              sessions = EXCLUDED.sessions,
              active_seconds = EXCLUDED.active_seconds,
              book_opens = EXCLUDED.book_opens,
              meaningful_reads = EXCLUDED.meaningful_reads,
              completions = EXCLUDED.completions,
              calculation_version = EXCLUDED.calculation_version,
              refreshed_at = EXCLUDED.refreshed_at,
              source_watermark = EXCLUDED.source_watermark
          """, VERSION, refreshed);
      jdbc.update("""
          INSERT INTO reporting.projection_health(
              projection_name, last_successful_refresh, source_watermark,
              record_count, definition_version, refresh_status, last_error_code)
          SELECT 'reader_day', ?, max(source_watermark), count(*), ?, 'SUCCESS', NULL
          FROM reporting.reader_day WHERE population = 'UNCLASSIFIED' AND platform = 'UNKNOWN'
          ON CONFLICT (projection_name) DO UPDATE SET
              last_successful_refresh = EXCLUDED.last_successful_refresh,
              source_watermark = EXCLUDED.source_watermark,
              record_count = EXCLUDED.record_count,
              definition_version = EXCLUDED.definition_version,
              refresh_status = EXCLUDED.refresh_status,
              last_error_code = NULL
          """, refreshed, VERSION);
    } catch (RuntimeException exception) {
      LOG.warn("reporting projection refresh failed error={}", exception.getClass().getSimpleName());
      jdbc.update("""
          INSERT INTO reporting.projection_health(
              projection_name, last_successful_refresh, source_watermark,
              record_count, definition_version, refresh_status, last_error_code)
          VALUES ('reader_day', NULL, NULL, 0, ?, 'ERROR', ?)
          ON CONFLICT (projection_name) DO UPDATE SET
              refresh_status = 'ERROR', last_error_code = EXCLUDED.last_error_code
          """, VERSION, exception.getClass().getSimpleName());
    }
  }
}
