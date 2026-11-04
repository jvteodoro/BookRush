package com.bookrush.adminservice.reporting;

import java.time.Instant;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Builds bounded, rebuildable reporting facts from the behavior export boundary. */
@Component
public class ReportingProjection {
  private static final Logger LOG = LoggerFactory.getLogger(ReportingProjection.class);
  private static final String VERSION = "events-v1-untrusted-population";
  private static final String FEED_VERSION = "feed-attribution-v1-untrusted-population";
  private static final String PIPELINE_VERSION = "analytics-pipeline-v1";
  private static final String QUALITY_VERSION = "event-quality-v1";
  private static final String PRODUCT_VERSION = "product-activity-v1-untrusted-population";
  private static final String CONTENT_VERSION = "content-supply-v1";
  private static final String INGESTION_VERSION = "ingestion-operations-v1";
  private static final String PHASE_VERSION = "analytics-phase-v1";
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public ReportingProjection(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Scheduled(fixedDelayString = "${bookrush.reporting.refresh-delay-ms:300000}")
  public void refresh() {
    Instant refreshed = Instant.now();
    runProjection("reader_day", VERSION, () -> refreshReaderDay(refreshed));
    runProjection("feed_daily", FEED_VERSION, () -> refreshFeedDaily(refreshed));
    runProjection("pipeline_snapshot", PIPELINE_VERSION, () -> refreshPipelineSnapshot(refreshed));
    runProjection("attribution_daily", QUALITY_VERSION, () -> refreshAttributionDaily(refreshed));
    runProjection("reconciliation_daily", QUALITY_VERSION, () -> refreshReconciliationDaily(refreshed));
    runProjection("social_daily", PRODUCT_VERSION, () -> refreshSocialDaily(refreshed));
    runProjection("publisher_daily", PRODUCT_VERSION, () -> refreshPublisherDaily(refreshed));
    runProjection("content_supply_snapshot", CONTENT_VERSION, () -> refreshContentSupply(refreshed));
    runProjection("ingestion_daily", INGESTION_VERSION, () -> refreshIngestionDaily(refreshed));
    runProjection("pipeline_phase_daily", PHASE_VERSION, () -> refreshPipelinePhaseDaily(refreshed));
  }

  private void refreshReaderDay(Instant refreshed) {
    jdbc.update("""
          INSERT INTO reporting.reader_day(
              day, population, platform, active_users, newly_active_users, sessions,
              weekly_active_users, monthly_active_users, returning_users,
              active_seconds, book_opens, meaningful_reads, completions,
              calculation_version, refreshed_at, source_watermark)
          WITH scoped AS (
            SELECT e.occurred_at, e.identity_subject, e.book_id, e.event_type, e.payload
            FROM behavior.events e
            WHERE e.identity_subject IS NOT NULL
              AND e.event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                'BOOK_COMPLETE', 'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE',
                'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
              AND e.occurred_at >= current_date - INTERVAL '396 days'
          ), first_seen AS (
            SELECT e.identity_subject, min(e.occurred_at::date) AS first_day
            FROM behavior.events e
            WHERE e.identity_subject IS NOT NULL
              AND e.event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                'BOOK_COMPLETE', 'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE',
                'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
            GROUP BY e.identity_subject
          ), daily AS (
            SELECT s.occurred_at::date AS day,
                count(DISTINCT s.identity_subject) AS active_users,
                count(DISTINCT s.identity_subject) FILTER (WHERE f.first_day = s.occurred_at::date) AS newly_active_users,
                count(DISTINCT s.identity_subject) FILTER (WHERE f.first_day < s.occurred_at::date) AS returning_users,
                count(*) FILTER (WHERE s.event_type = 'READ_SESSION') AS sessions,
                coalesce(sum(CASE WHEN s.event_type = 'READ_SESSION'
                    AND s.payload->>'activeSeconds' ~ '^[0-9]+$'
                  THEN LEAST((s.payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0) AS active_seconds,
                count(*) FILTER (WHERE s.event_type = 'BOOK_OPEN') AS book_opens,
                count(DISTINCT (s.identity_subject, s.book_id)) FILTER (
                  WHERE s.event_type = 'READ_SESSION'
                    AND s.payload->>'activeSeconds' ~ '^[0-9]+$'
                    AND (s.payload->>'activeSeconds')::bigint >= 60) AS meaningful_reads,
                count(DISTINCT (s.identity_subject, s.book_id)) FILTER (
                  WHERE s.event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
                max(s.occurred_at) AS source_watermark
            FROM scoped s JOIN first_seen f ON f.identity_subject = s.identity_subject
            GROUP BY s.occurred_at::date
          )
          SELECT d.day, 'UNCLASSIFIED', 'UNKNOWN', d.active_users, d.newly_active_users, d.sessions,
              (SELECT count(DISTINCT w.identity_subject) FROM scoped w
                WHERE w.occurred_at >= d.day - INTERVAL '6 days'
                  AND w.occurred_at < d.day + INTERVAL '1 day'),
              (SELECT count(DISTINCT m.identity_subject) FROM scoped m
                WHERE m.occurred_at >= d.day - INTERVAL '29 days'
                  AND m.occurred_at < d.day + INTERVAL '1 day'),
              d.returning_users, d.active_seconds, d.book_opens,
              d.meaningful_reads, d.completions, ?, ?, d.source_watermark
          FROM daily d
          WHERE d.day >= current_date - INTERVAL '366 days'
          ON CONFLICT (day, population, platform) DO UPDATE SET
              active_users = EXCLUDED.active_users,
              newly_active_users = EXCLUDED.newly_active_users,
              weekly_active_users = EXCLUDED.weekly_active_users,
              monthly_active_users = EXCLUDED.monthly_active_users,
              returning_users = EXCLUDED.returning_users,
              sessions = EXCLUDED.sessions,
              active_seconds = EXCLUDED.active_seconds,
              book_opens = EXCLUDED.book_opens,
              meaningful_reads = EXCLUDED.meaningful_reads,
              completions = EXCLUDED.completions,
              calculation_version = EXCLUDED.calculation_version,
              refreshed_at = EXCLUDED.refreshed_at,
              source_watermark = EXCLUDED.source_watermark
          """, VERSION, Timestamp.from(refreshed));
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
          """, Timestamp.from(refreshed), VERSION);
  }

  private void runProjection(String name, String version, Runnable projection) {
    try {
      transactions.executeWithoutResult(status -> projection.run());
    } catch (RuntimeException exception) {
      String error = exception.getClass().getSimpleName();
      LOG.warn("reporting projection refresh failed projection={} error={} detail={}",
          name, error, safeDatabaseError(exception));
      jdbc.update("""
          INSERT INTO reporting.projection_health(
              projection_name, last_successful_refresh, source_watermark,
              record_count, definition_version, refresh_status, last_error_code)
          VALUES (?, NULL, NULL, 0, ?, 'ERROR', ?)
          ON CONFLICT (projection_name) DO UPDATE SET
              refresh_status = 'ERROR', last_error_code = EXCLUDED.last_error_code
          """, name, version, error);
    }
  }

  private String safeDatabaseError(RuntimeException exception) {
    Throwable root = exception;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String detail = root.getMessage();
    if (detail == null || detail.isBlank()) return "unspecified";
    String normalized = detail.replaceAll("\\s+", " ");
    return normalized.substring(0, Math.min(normalized.length(), 240));
  }

  private void refreshFeedDaily(Instant refreshed) {
    jdbc.update("""
        WITH exposure AS (
          SELECT i.id AS impression_id, i.item_id, i.shown_at, i.viewable,
              r.model_version, r.strategy,
              COALESCE(r.context->>'personalizationPath', 'UNKNOWN') AS feed_path,
              COALESCE(r.context->>'platform', 'UNKNOWN') AS platform
          FROM recommendation.impression i
          JOIN recommendation.request r ON r.id = i.request_id
          WHERE i.shown_at >= current_date - INTERVAL '366 days'
        ), outcome AS (
          SELECT oa.source_event_id, oa.impression_id, oa.recommendation_item_id,
              oa.outcome_type, e.payload, e.occurred_at
          FROM behavior.outcome_attribution oa
          JOIN behavior.events e ON e.id = oa.source_event_id
          WHERE oa.attribution_status = 'ATTRIBUTED'
        )
        INSERT INTO reporting.feed_daily(
            day, model_version, strategy, feed_path, population, platform,
            served_count, viewable_count, attributed_open_count,
            attributed_read_10_count, attributed_read_50_count,
            attributed_complete_count, dwell_observations, dwell_seconds,
            calculation_version, refreshed_at, source_watermark)
        SELECT x.day, x.model_version, x.strategy, x.feed_path, 'UNCLASSIFIED', x.platform,
            count(DISTINCT x.impression_id),
            count(DISTINCT x.impression_id) FILTER (WHERE x.viewable),
            count(DISTINCT x.source_event_id) FILTER (WHERE x.outcome_type = 'BOOK_OPEN'),
            count(DISTINCT x.source_event_id) FILTER (
              WHERE x.outcome_type = 'READ_PROGRESS'
                AND x.payload->>'percent' ~ '^[0-9]+(\\.[0-9]+)?$'
                AND (x.payload->>'percent')::numeric >= 10),
            count(DISTINCT x.source_event_id) FILTER (
              WHERE x.outcome_type = 'READ_PROGRESS'
                AND x.payload->>'percent' ~ '^[0-9]+(\\.[0-9]+)?$'
                AND (x.payload->>'percent')::numeric >= 50),
            count(DISTINCT x.source_event_id) FILTER (
              WHERE x.outcome_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')),
            NULL, NULL, ?, ?, max(x.source_watermark)
        FROM (
          SELECT e.*, o.source_event_id, o.outcome_type, o.payload,
              GREATEST(e.shown_at, COALESCE(o.occurred_at, e.shown_at)) AS source_watermark,
              e.shown_at::date AS day
          FROM exposure e
          LEFT JOIN outcome o ON o.impression_id = e.impression_id
              OR o.recommendation_item_id = e.item_id
        ) x
        GROUP BY x.day, x.model_version, x.strategy, x.feed_path, x.platform
        ON CONFLICT (day, model_version, strategy, feed_path, population, platform) DO UPDATE SET
          served_count = EXCLUDED.served_count,
          viewable_count = EXCLUDED.viewable_count,
          attributed_open_count = EXCLUDED.attributed_open_count,
          attributed_read_10_count = EXCLUDED.attributed_read_10_count,
          attributed_read_50_count = EXCLUDED.attributed_read_50_count,
          attributed_complete_count = EXCLUDED.attributed_complete_count,
          dwell_observations = EXCLUDED.dwell_observations,
          dwell_seconds = EXCLUDED.dwell_seconds,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at,
          source_watermark = EXCLUDED.source_watermark
        """, FEED_VERSION, Timestamp.from(refreshed));
    updateHealth("feed_daily", FEED_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.feed_daily");
  }

  private void refreshPipelineSnapshot(Instant refreshed) {
    jdbc.update("""
        INSERT INTO reporting.pipeline_snapshot(
            collected_at, service, job_status, phase, status_count,
            oldest_pending_age_seconds, failure_count, throughput,
            calculation_version, source_watermark)
        SELECT date_trunc('minute', ?::timestamptz), 'book-analytics-service', i.status, 'ITEM',
            count(*),
            CASE WHEN i.status IN ('PENDING', 'RUNNING')
              THEN EXTRACT(EPOCH FROM (clock_timestamp() - min(i.created_at)))::bigint
              ELSE NULL END,
            count(*) FILTER (WHERE i.status = 'FAILED'),
            count(*) FILTER (WHERE i.status = 'COMPLETED'
                AND i.finished_at >= clock_timestamp() - INTERVAL '1 hour'),
            ?, max(coalesce(i.finished_at, i.started_at, i.created_at))
        FROM analytics.analysis_job_item i
        GROUP BY i.status
        ON CONFLICT (collected_at, service, job_status, phase) DO UPDATE SET
            status_count = EXCLUDED.status_count,
            oldest_pending_age_seconds = EXCLUDED.oldest_pending_age_seconds,
            failure_count = EXCLUDED.failure_count,
            throughput = EXCLUDED.throughput,
            calculation_version = EXCLUDED.calculation_version,
            source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), PIPELINE_VERSION);
    jdbc.update("""
        INSERT INTO reporting.pipeline_snapshot(
            collected_at, service, job_status, phase, status_count,
            oldest_pending_age_seconds, failure_count, throughput,
            calculation_version, source_watermark)
        SELECT date_trunc('minute', ?::timestamptz), 'book-analytics-service', p.status, p.phase,
            count(*), NULL,
            count(*) FILTER (WHERE p.status = 'FAILED'),
            count(*) FILTER (WHERE p.status = 'COMPLETED'
                AND p.finished_at >= clock_timestamp() - INTERVAL '1 hour'),
            ?, max(coalesce(p.finished_at, p.started_at))
        FROM analytics.analysis_phase_timing p
        GROUP BY p.status, p.phase
        ON CONFLICT (collected_at, service, job_status, phase) DO UPDATE SET
            status_count = EXCLUDED.status_count,
            failure_count = EXCLUDED.failure_count,
            throughput = EXCLUDED.throughput,
            calculation_version = EXCLUDED.calculation_version,
            source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), PIPELINE_VERSION);
    updateHealth("pipeline_snapshot", PIPELINE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.pipeline_snapshot");
  }

  private void refreshAttributionDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.attribution_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.attribution_daily(
            day, attribution_status, outcome_count, unique_events,
            calculation_version, refreshed_at, source_watermark)
        SELECT occurred_at::date, attribution_status, count(*), count(DISTINCT source_event_id),
            ?, ?, max(occurred_at)
        FROM behavior.outcome_attribution
        WHERE occurred_at >= current_date - INTERVAL '366 days'
        GROUP BY occurred_at::date, attribution_status
        """, QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("attribution_daily", QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.attribution_daily");
  }

  private void refreshReconciliationDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.reconciliation_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH ledger AS (
          SELECT shown_at::date AS day, count(DISTINCT id) AS impression_count,
              count(DISTINCT id) FILTER (WHERE viewable) AS viewable_count,
              max(shown_at) AS source_watermark
          FROM recommendation.impression
          WHERE shown_at >= current_date - INTERVAL '366 days'
          GROUP BY shown_at::date
        ), events AS (
          SELECT e.occurred_at::date AS day,
              count(*) FILTER (WHERE e.event_type = 'EXCERPT_IMPRESSION'
                AND e.payload->>'impressionId' IS NOT NULL) AS event_count,
              count(DISTINCT e.payload->>'impressionId') FILTER (
                WHERE e.event_type = 'EXCERPT_IMPRESSION'
                  AND e.payload->>'impressionId' IS NOT NULL) AS unique_count,
              count(DISTINCT e.payload->>'impressionId') FILTER (
                WHERE e.event_type = 'EXCERPT_IMPRESSION'
                  AND e.payload->>'impressionId' IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM recommendation.impression i
                    WHERE i.id::text = e.payload->>'impressionId')) AS mismatch_count,
              max(e.occurred_at) AS source_watermark
          FROM behavior.events e
          WHERE e.occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY e.occurred_at::date
        )
        INSERT INTO reporting.reconciliation_daily(
            day, ledger_impression_count, ledger_viewable_count,
            behavior_impression_event_count, behavior_unique_impression_count,
            mismatched_impression_count, duplicate_event_count,
            calculation_version, refreshed_at, source_watermark)
        SELECT COALESCE(l.day, e.day), COALESCE(l.impression_count, 0),
            COALESCE(l.viewable_count, 0), COALESCE(e.event_count, 0),
            COALESCE(e.unique_count, 0), COALESCE(e.mismatch_count, 0),
            GREATEST(COALESCE(e.event_count, 0) - COALESCE(e.unique_count, 0), 0),
            ?, ?, GREATEST(l.source_watermark, e.source_watermark)
        FROM ledger l FULL OUTER JOIN events e ON e.day = l.day
        """, QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("reconciliation_daily", QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.reconciliation_daily");
  }

  private void refreshSocialDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.social_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH activity AS (
          SELECT occurred_at::date AS day,
              count(*) FILTER (WHERE activity_type = 'FOLLOW') AS follow_count,
              count(*) FILTER (WHERE activity_type = 'LIKE') AS like_count,
              count(*) FILTER (WHERE activity_type = 'COMMENT') AS comment_count,
              count(*) FILTER (WHERE activity_type = 'SHARE') AS share_count,
              count(*) FILTER (WHERE activity_type = 'BOOK_OPENED') AS book_opened_count,
              count(*) FILTER (WHERE activity_type = 'BOOK_SAVED') AS book_saved_count,
              count(DISTINCT actor_subject_key) AS unique_actors,
              max(occurred_at) AS source_watermark
          FROM social.activity
          WHERE occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
        ), reports AS (
          SELECT created_at::date AS day, count(*) FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')) AS reports_open,
              max(created_at) AS source_watermark
          FROM social.reports
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), moderation AS (
          SELECT updated_at::date AS day, count(*) FILTER (WHERE visibility <> 'VISIBLE') AS moderation_backlog,
              max(updated_at) AS source_watermark
          FROM social.comment_moderation_state
          WHERE updated_at >= current_date - INTERVAL '366 days'
          GROUP BY updated_at::date
        )
        INSERT INTO reporting.social_daily(
            day, follow_count, like_count, comment_count, share_count,
            book_opened_count, book_saved_count, unique_actors, reports_open,
            moderation_backlog, calculation_version, refreshed_at, source_watermark)
        SELECT COALESCE(a.day, r.day, m.day), COALESCE(a.follow_count, 0),
            COALESCE(a.like_count, 0), COALESCE(a.comment_count, 0), COALESCE(a.share_count, 0),
            COALESCE(a.book_opened_count, 0), COALESCE(a.book_saved_count, 0),
            COALESCE(a.unique_actors, 0), COALESCE(r.reports_open, 0),
            COALESCE(m.moderation_backlog, 0), ?, ?,
            GREATEST(a.source_watermark, r.source_watermark, m.source_watermark)
        FROM activity a FULL OUTER JOIN reports r ON r.day = a.day
          FULL OUTER JOIN moderation m ON m.day = COALESCE(a.day, r.day)
        """, PRODUCT_VERSION, Timestamp.from(refreshed));
    updateHealth("social_daily", PRODUCT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.social_daily");
  }

  private void refreshPublisherDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.publisher_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.publisher_daily(
            day, published_work_count, impressions, opens, reads, likes, shares, comments,
            calculation_version, refreshed_at, source_watermark)
        SELECT m.day, count(DISTINCT m.published_work_id), coalesce(sum(m.impressions), 0),
            coalesce(sum(m.opens), 0), coalesce(sum(m.reads), 0), coalesce(sum(m.likes), 0),
            coalesce(sum(m.shares), 0), coalesce(sum(m.comments), 0), ?, ?, max(m.updated_at)
        FROM publisher.daily_publication_metric m
        JOIN publisher.published_work w ON w.id = m.published_work_id
        WHERE m.day >= current_date - INTERVAL '366 days'
        GROUP BY m.day
        """, PRODUCT_VERSION, Timestamp.from(refreshed));
    updateHealth("publisher_daily", PRODUCT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.publisher_daily");
  }

  private void refreshContentSupply(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.content_supply_snapshot WHERE collected_at < now() - INTERVAL '30 days'");
    jdbc.update("""
        INSERT INTO reporting.content_supply_snapshot(
            collected_at, active_books, available_asset_versions,
            analyzed_asset_versions, eligible_excerpts, ranked_excerpts,
            calculation_version, refreshed_at, source_watermark)
        SELECT date_trunc('minute', ?::timestamptz),
            (SELECT count(*) FROM catalog.book WHERE status = 'ACTIVE'),
            (SELECT count(*) FROM catalog.book_asset_version WHERE status = 'AVAILABLE'),
            (SELECT count(DISTINCT input_asset_version_id)
               FROM analytics.analysis_run
              WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')),
            (SELECT count(*) FROM analytics.excerpt WHERE body_eligible),
            (SELECT count(*) FROM analytics.excerpt_rank), ?, ?,
            GREATEST(
              (SELECT max(updated_at) FROM catalog.book),
              (SELECT max(updated_at) FROM catalog.book_asset_version),
              (SELECT max(created_at) FROM analytics.excerpt_rank))
        ON CONFLICT (collected_at) DO UPDATE SET
            active_books = EXCLUDED.active_books,
            available_asset_versions = EXCLUDED.available_asset_versions,
            analyzed_asset_versions = EXCLUDED.analyzed_asset_versions,
            eligible_excerpts = EXCLUDED.eligible_excerpts,
            ranked_excerpts = EXCLUDED.ranked_excerpts,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), CONTENT_VERSION, Timestamp.from(refreshed));
    updateHealth("content_supply_snapshot", CONTENT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.content_supply_snapshot");
  }

  private void refreshIngestionDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.ingestion_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.ingestion_daily(
            day, job_status, job_count, discovered_items, processed_items,
            succeeded_items, failed_items, item_status, item_count,
            calculation_version, refreshed_at, source_watermark)
        SELECT j.created_at::date, j.status, count(DISTINCT j.id),
            coalesce(sum(j.items_discovered), 0), coalesce(sum(j.items_processed), 0),
            coalesce(sum(j.items_succeeded), 0), coalesce(sum(j.items_failed), 0),
            coalesce(i.status, 'NO_ITEMS'), count(i.id), ?, ?,
            GREATEST(max(j.updated_at), max(i.updated_at))
        FROM catalog.ingestion_job j
        LEFT JOIN catalog.ingestion_item i ON i.ingestion_job_id = j.id
        WHERE j.created_at >= current_date - INTERVAL '366 days'
        GROUP BY j.created_at::date, j.status, i.status
        ON CONFLICT (day, job_status, item_status) DO UPDATE SET
            job_count = EXCLUDED.job_count,
            discovered_items = EXCLUDED.discovered_items,
            processed_items = EXCLUDED.processed_items,
            succeeded_items = EXCLUDED.succeeded_items,
            failed_items = EXCLUDED.failed_items,
            item_count = EXCLUDED.item_count,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, INGESTION_VERSION, Timestamp.from(refreshed));
    updateHealth("ingestion_daily", INGESTION_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.ingestion_daily");
  }

  private void refreshPipelinePhaseDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.pipeline_phase_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.pipeline_phase_daily(
            day, phase, phase_status, error_code, attempts, completed_count,
            failed_count, stuck_count, p50_duration_ms, p95_duration_ms,
            p99_duration_ms, calculation_version, refreshed_at, source_watermark)
        SELECT p.started_at::date, p.phase, p.status,
            coalesce(p.error_code, 'NONE'), count(*),
            count(*) FILTER (WHERE p.status = 'COMPLETED'),
            count(*) FILTER (WHERE p.status = 'FAILED'),
            count(*) FILTER (WHERE p.status = 'RUNNING'
              AND p.started_at < clock_timestamp() - INTERVAL '15 minutes'),
            percentile_cont(0.50) WITHIN GROUP (ORDER BY p.duration_ms)
              FILTER (WHERE p.duration_ms IS NOT NULL),
            percentile_cont(0.95) WITHIN GROUP (ORDER BY p.duration_ms)
              FILTER (WHERE p.duration_ms IS NOT NULL),
            percentile_cont(0.99) WITHIN GROUP (ORDER BY p.duration_ms)
              FILTER (WHERE p.duration_ms IS NOT NULL),
            ?, ?, greatest(max(p.finished_at), max(p.started_at))
        FROM analytics.analysis_phase_timing p
        WHERE p.started_at >= current_date - INTERVAL '366 days'
        GROUP BY p.started_at::date, p.phase, p.status, coalesce(p.error_code, 'NONE')
        ON CONFLICT (day, phase, phase_status, error_code) DO UPDATE SET
            attempts = EXCLUDED.attempts,
            completed_count = EXCLUDED.completed_count,
            failed_count = EXCLUDED.failed_count,
            stuck_count = EXCLUDED.stuck_count,
            p50_duration_ms = EXCLUDED.p50_duration_ms,
            p95_duration_ms = EXCLUDED.p95_duration_ms,
            p99_duration_ms = EXCLUDED.p99_duration_ms,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, PHASE_VERSION, Timestamp.from(refreshed));
    updateHealth("pipeline_phase_daily", PHASE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.pipeline_phase_daily");
  }

  private void updateHealth(String name, String version, Instant refreshed, String sourceQuery) {
    jdbc.update("""
        INSERT INTO reporting.projection_health(
            projection_name, last_successful_refresh, source_watermark,
            record_count, definition_version, refresh_status, last_error_code)
        SELECT ?, ?, q.source_watermark, q.record_count, ?, 'SUCCESS', NULL
        FROM (%s) q
        ON CONFLICT (projection_name) DO UPDATE SET
            last_successful_refresh = EXCLUDED.last_successful_refresh,
            source_watermark = EXCLUDED.source_watermark,
            record_count = EXCLUDED.record_count,
            definition_version = EXCLUDED.definition_version,
            refresh_status = EXCLUDED.refresh_status,
            last_error_code = NULL
        """.formatted(sourceQuery), name, Timestamp.from(refreshed), version);
  }
}
