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
  private static final String READING_VERSION = "reading-engagement-v1-untrusted-population";
  private static final String RECOMMENDATION_VERSION = "recommendation-ledger-v1-untrusted-population";
  private static final String EVENT_QUALITY_VERSION = "event-quality-ingestion-v1";
  private static final String GOVERNANCE_VERSION = "governance-activity-v1-untrusted-population";
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
    runProjection("reading_daily", READING_VERSION, () -> refreshReadingDaily(refreshed));
    runProjection("retention_cohort", READING_VERSION, () -> refreshRetentionCohort(refreshed));
    runProjection("recommendation_daily", RECOMMENDATION_VERSION, () -> refreshRecommendationDaily(refreshed));
    runProjection("event_quality_daily", EVENT_QUALITY_VERSION, () -> refreshEventQualityDaily(refreshed));
    runProjection("moderation_daily", GOVERNANCE_VERSION, () -> refreshModerationDaily(refreshed));
    runProjection("publisher_workflow_daily", GOVERNANCE_VERSION, () -> refreshPublisherWorkflowDaily(refreshed));
    runProjection("rights_daily", GOVERNANCE_VERSION, () -> refreshRightsDaily(refreshed));
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

  private void refreshReadingDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.reading_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH events AS (
          SELECT occurred_at::date AS day,
              count(DISTINCT identity_subject) AS unique_readers,
              count(DISTINCT (identity_subject, book_id)) FILTER (WHERE event_type = 'BOOK_OPEN') AS books_opened,
              count(DISTINCT (identity_subject, book_id)) FILTER (WHERE event_type = 'BOOK_OPEN') AS books_started,
              count(DISTINCT (identity_subject, book_id)) FILTER (WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
              max(occurred_at) AS source_watermark
          FROM behavior.events
          WHERE identity_subject IS NOT NULL
            AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS', 'BOOK_COMPLETE', 'READ_COMPLETE')
            AND occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
        ), sessions AS (
          SELECT started_at::date AS day, count(*) AS sessions,
              coalesce(sum(least(greatest(active_seconds, 0), 86400)), 0) AS active_seconds,
              percentile_cont(0.50) WITHIN GROUP (ORDER BY least(greatest(active_seconds, 0), 86400)) AS p50_session_seconds,
              percentile_cont(0.90) WITHIN GROUP (ORDER BY least(greatest(active_seconds, 0), 86400)) AS p90_session_seconds,
              max(coalesce(ended_at, started_at)) AS source_watermark
          FROM reader_state.session
          WHERE started_at >= current_date - INTERVAL '366 days'
          GROUP BY started_at::date
        ), library AS (
          SELECT created_at::date AS day, count(DISTINCT book_id) AS saved_books,
              count(DISTINCT subject_key) AS unique_savers, max(created_at) AS source_watermark
          FROM reader_state.library
          WHERE status = 'SAVED' AND created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        )
        INSERT INTO reporting.reading_daily(
            day, unique_readers, sessions, active_seconds, p50_session_seconds,
            p90_session_seconds, books_opened, books_started, completions,
            saved_books, unique_savers, calculation_version, refreshed_at, source_watermark)
        SELECT coalesce(e.day, s.day, l.day), coalesce(e.unique_readers, 0),
            coalesce(s.sessions, 0), coalesce(s.active_seconds, 0),
            s.p50_session_seconds, s.p90_session_seconds,
            coalesce(e.books_opened, 0), coalesce(e.books_started, 0), coalesce(e.completions, 0),
            coalesce(l.saved_books, 0), coalesce(l.unique_savers, 0), ?, ?,
            greatest(coalesce(e.source_watermark, '-infinity'::timestamptz),
              coalesce(s.source_watermark, '-infinity'::timestamptz),
              coalesce(l.source_watermark, '-infinity'::timestamptz))
        FROM events e FULL OUTER JOIN sessions s ON s.day = e.day
          FULL OUTER JOIN library l ON l.day = coalesce(e.day, s.day)
        ON CONFLICT (day) DO UPDATE SET
            unique_readers = EXCLUDED.unique_readers,
            sessions = EXCLUDED.sessions,
            active_seconds = EXCLUDED.active_seconds,
            p50_session_seconds = EXCLUDED.p50_session_seconds,
            p90_session_seconds = EXCLUDED.p90_session_seconds,
            books_opened = EXCLUDED.books_opened,
            books_started = EXCLUDED.books_started,
            completions = EXCLUDED.completions,
            saved_books = EXCLUDED.saved_books,
            unique_savers = EXCLUDED.unique_savers,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, READING_VERSION, Timestamp.from(refreshed));
    updateHealth("reading_daily", READING_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.reading_daily");
  }

  private void refreshRetentionCohort(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.retention_cohort WHERE cohort_day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH significant AS (
          SELECT identity_subject, occurred_at::date AS activity_day
          FROM behavior.events
          WHERE identity_subject IS NOT NULL
            AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS', 'BOOK_COMPLETE', 'READ_COMPLETE',
              'BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
        ), first_seen AS (
          SELECT identity_subject, min(activity_day) AS cohort_day
          FROM significant GROUP BY identity_subject
        ), cohorts AS (
          SELECT cohort_day, count(*) AS cohort_users
          FROM first_seen
          WHERE cohort_day >= current_date - INTERVAL '366 days'
          GROUP BY cohort_day
        ), offsets AS (
          SELECT c.cohort_day, o.offset_day, c.cohort_users,
              count(DISTINCT s.identity_subject) AS retained_users
          FROM cohorts c CROSS JOIN (VALUES (0), (1), (7), (30)) o(offset_day)
          LEFT JOIN first_seen f ON f.cohort_day = c.cohort_day
          LEFT JOIN significant s ON s.identity_subject = f.identity_subject
            AND s.activity_day = c.cohort_day + o.offset_day
          GROUP BY c.cohort_day, o.offset_day, c.cohort_users
        )
        INSERT INTO reporting.retention_cohort(
            cohort_day, offset_day, cohort_users, retained_users, mature,
            calculation_version, refreshed_at, source_watermark)
        SELECT cohort_day, offset_day, cohort_users, retained_users,
            current_date >= cohort_day + offset_day, ?, ?,
            (SELECT max(occurred_at) FROM behavior.events)
        FROM offsets
        ON CONFLICT (cohort_day, offset_day) DO UPDATE SET
            cohort_users = EXCLUDED.cohort_users,
            retained_users = EXCLUDED.retained_users,
            mature = EXCLUDED.mature,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, READING_VERSION, Timestamp.from(refreshed));
    updateHealth("retention_cohort", READING_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.retention_cohort");
  }

  private void refreshRecommendationDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH requests AS (
          SELECT r.id, r.created_at::date AS day, r.model_version, r.strategy,
              coalesce(r.context->>'personalizationPath', 'UNKNOWN') AS feed_path,
              coalesce(r.context->>'platform', 'UNKNOWN') AS platform,
              r.created_at AS source_watermark
          FROM recommendation.request r
          WHERE r.created_at >= current_date - INTERVAL '366 days'
        ), items AS (
          SELECT i.request_id, count(*) AS item_count, count(DISTINCT i.book_id) AS unique_books,
              percentile_cont(0.50) WITHIN GROUP (ORDER BY i.rank) AS p50_rank,
              percentile_cont(0.90) WITHIN GROUP (ORDER BY i.rank) AS p90_rank,
              max(i.created_at) AS source_watermark
          FROM recommendation.item i JOIN requests r ON r.id = i.request_id
          GROUP BY i.request_id
        ), impressions AS (
          SELECT i.request_id, count(*) AS impression_count,
              count(*) FILTER (WHERE i.viewable) AS viewable_count,
              max(i.shown_at) AS source_watermark
          FROM recommendation.impression i JOIN requests r ON r.id = i.request_id
          GROUP BY i.request_id
        )
        INSERT INTO reporting.recommendation_daily(
            day, model_version, strategy, feed_path, population, platform,
            request_count, item_count, impression_count, viewable_count,
            unique_books, p50_rank, p90_rank, calculation_version, refreshed_at,
            source_watermark)
        SELECT r.day, r.model_version, r.strategy, r.feed_path, 'UNCLASSIFIED', r.platform,
            count(*), coalesce(sum(i.item_count), 0), coalesce(sum(m.impression_count), 0),
            coalesce(sum(m.viewable_count), 0), coalesce(sum(i.unique_books), 0),
            percentile_cont(0.50) WITHIN GROUP (ORDER BY i.p50_rank) FILTER (WHERE i.p50_rank IS NOT NULL),
            percentile_cont(0.90) WITHIN GROUP (ORDER BY i.p90_rank) FILTER (WHERE i.p90_rank IS NOT NULL),
            ?, ?, greatest(max(r.source_watermark), max(i.source_watermark), max(m.source_watermark))
        FROM requests r LEFT JOIN items i ON i.request_id = r.id
          LEFT JOIN impressions m ON m.request_id = r.id
        GROUP BY r.day, r.model_version, r.strategy, r.feed_path, r.platform
        ON CONFLICT (day, model_version, strategy, feed_path, population, platform) DO UPDATE SET
            request_count = EXCLUDED.request_count,
            item_count = EXCLUDED.item_count,
            impression_count = EXCLUDED.impression_count,
            viewable_count = EXCLUDED.viewable_count,
            unique_books = EXCLUDED.unique_books,
            p50_rank = EXCLUDED.p50_rank,
            p90_rank = EXCLUDED.p90_rank,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, RECOMMENDATION_VERSION, Timestamp.from(refreshed));
    updateHealth("recommendation_daily", RECOMMENDATION_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.recommendation_daily");
  }

  private void refreshEventQualityDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.event_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH accepted AS (
          SELECT occurred_at::date AS day, count(*) AS accepted_events,
              max(received_at) AS source_watermark
          FROM behavior.events
          WHERE occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
        ), rejected AS (
          SELECT received_at::date AS day, count(*) AS rejected_events,
              count(*) FILTER (WHERE reason ILIKE '%duplicate%') AS duplicate_rejections,
              count(*) FILTER (WHERE reason ILIKE '%schema%') AS schema_rejections,
              count(*) FILTER (WHERE reason ILIKE '%payload%') AS payload_rejections,
              max(received_at) AS source_watermark
          FROM behavior.rejects
          WHERE received_at >= current_date - INTERVAL '366 days'
          GROUP BY received_at::date
        ), outbox AS (
          SELECT created_at::date AS day,
              count(*) FILTER (WHERE published_at IS NULL) AS outbox_pending,
              count(*) FILTER (WHERE published_at IS NOT NULL) AS outbox_published,
              coalesce(sum(attempts) FILTER (WHERE attempts > 0), 0) AS outbox_failed_attempts,
              extract(epoch FROM clock_timestamp() - min(created_at)
                FILTER (WHERE published_at IS NULL))::bigint AS outbox_oldest_pending_age_seconds,
              max(coalesce(published_at, created_at)) AS source_watermark
          FROM behavior.outbox
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        )
        INSERT INTO reporting.event_quality_daily(
            day, accepted_events, rejected_events, duplicate_rejections,
            schema_rejections, payload_rejections, outbox_pending,
            outbox_published, outbox_failed_attempts,
            outbox_oldest_pending_age_seconds, calculation_version,
            refreshed_at, source_watermark)
        SELECT coalesce(a.day, r.day, o.day), coalesce(a.accepted_events, 0),
            coalesce(r.rejected_events, 0), coalesce(r.duplicate_rejections, 0),
            coalesce(r.schema_rejections, 0), coalesce(r.payload_rejections, 0),
            coalesce(o.outbox_pending, 0), coalesce(o.outbox_published, 0),
            coalesce(o.outbox_failed_attempts, 0), o.outbox_oldest_pending_age_seconds,
            ?, ?, greatest(coalesce(a.source_watermark, '-infinity'::timestamptz),
              coalesce(r.source_watermark, '-infinity'::timestamptz),
              coalesce(o.source_watermark, '-infinity'::timestamptz))
        FROM accepted a FULL OUTER JOIN rejected r ON r.day = a.day
          FULL OUTER JOIN outbox o ON o.day = coalesce(a.day, r.day)
        ON CONFLICT (day) DO UPDATE SET
            accepted_events = EXCLUDED.accepted_events,
            rejected_events = EXCLUDED.rejected_events,
            duplicate_rejections = EXCLUDED.duplicate_rejections,
            schema_rejections = EXCLUDED.schema_rejections,
            payload_rejections = EXCLUDED.payload_rejections,
            outbox_pending = EXCLUDED.outbox_pending,
            outbox_published = EXCLUDED.outbox_published,
            outbox_failed_attempts = EXCLUDED.outbox_failed_attempts,
            outbox_oldest_pending_age_seconds = EXCLUDED.outbox_oldest_pending_age_seconds,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EVENT_QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("event_quality_daily", EVENT_QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.event_quality_daily");
  }

  private void refreshModerationDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.moderation_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.moderation_daily(
            day, open_count, in_review_count, resolved_count, dismissed_count,
            p50_resolution_seconds, p95_resolution_seconds, oldest_open_age_seconds,
            calculation_version, refreshed_at, source_watermark)
        SELECT created_at::date,
            count(*) FILTER (WHERE status = 'OPEN'),
            count(*) FILTER (WHERE status = 'IN_REVIEW'),
            count(*) FILTER (WHERE status = 'RESOLVED'),
            count(*) FILTER (WHERE status = 'DISMISSED'),
            percentile_cont(0.50) WITHIN GROUP (ORDER BY extract(epoch FROM resolved_at - created_at))
              FILTER (WHERE resolved_at IS NOT NULL),
            percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM resolved_at - created_at))
              FILTER (WHERE resolved_at IS NOT NULL),
            extract(epoch FROM clock_timestamp() - min(created_at))::bigint
              FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')),
            ?, ?, max(coalesce(resolved_at, created_at))
        FROM social.reports
        WHERE created_at >= current_date - INTERVAL '366 days'
        GROUP BY created_at::date
        ON CONFLICT (day) DO UPDATE SET
            open_count = EXCLUDED.open_count,
            in_review_count = EXCLUDED.in_review_count,
            resolved_count = EXCLUDED.resolved_count,
            dismissed_count = EXCLUDED.dismissed_count,
            p50_resolution_seconds = EXCLUDED.p50_resolution_seconds,
            p95_resolution_seconds = EXCLUDED.p95_resolution_seconds,
            oldest_open_age_seconds = EXCLUDED.oldest_open_age_seconds,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, GOVERNANCE_VERSION, Timestamp.from(refreshed));
    updateHealth("moderation_daily", GOVERNANCE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.moderation_daily");
  }

  private void refreshPublisherWorkflowDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.publisher_workflow_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH submissions AS (
          SELECT created_at::date AS day, count(*) AS submissions, max(updated_at) AS source_watermark
          FROM publisher.submission
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), reviews AS (
          SELECT created_at::date AS day,
              count(*) FILTER (WHERE status = 'APPROVED') AS approved_reviews,
              count(*) FILTER (WHERE status = 'REJECTED') AS rejected_reviews,
              count(*) FILTER (WHERE status = 'CHANGES_REQUESTED') AS changes_requested,
              max(created_at) AS source_watermark
          FROM publisher.publication_review
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), works AS (
          SELECT published_at::date AS day, count(*) AS published_works,
              count(*) FILTER (WHERE status = 'PUBLISHED') AS active_published_works,
              max(published_at) AS source_watermark
          FROM publisher.published_work
          WHERE published_at >= current_date - INTERVAL '366 days'
          GROUP BY published_at::date
        ), rights AS (
          SELECT affirmed_at::date AS day, count(*) AS rights_attestations,
              max(affirmed_at) AS source_watermark
          FROM publisher.rights_attestation
          WHERE affirmed_at >= current_date - INTERVAL '366 days'
          GROUP BY affirmed_at::date
        )
        INSERT INTO reporting.publisher_workflow_daily(
            day, submissions, approved_reviews, rejected_reviews, changes_requested,
            published_works, active_published_works, rights_attestations,
            calculation_version, refreshed_at, source_watermark)
        SELECT coalesce(s.day, r.day, w.day, a.day), coalesce(s.submissions, 0),
            coalesce(r.approved_reviews, 0), coalesce(r.rejected_reviews, 0),
            coalesce(r.changes_requested, 0), coalesce(w.published_works, 0),
            coalesce(w.active_published_works, 0), coalesce(a.rights_attestations, 0),
            ?, ?, greatest(coalesce(s.source_watermark, '-infinity'::timestamptz),
              coalesce(r.source_watermark, '-infinity'::timestamptz),
              coalesce(w.source_watermark, '-infinity'::timestamptz),
              coalesce(a.source_watermark, '-infinity'::timestamptz))
        FROM submissions s FULL OUTER JOIN reviews r ON r.day = s.day
          FULL OUTER JOIN works w ON w.day = coalesce(s.day, r.day)
          FULL OUTER JOIN rights a ON a.day = coalesce(s.day, r.day, w.day)
        ON CONFLICT (day) DO UPDATE SET
            submissions = EXCLUDED.submissions,
            approved_reviews = EXCLUDED.approved_reviews,
            rejected_reviews = EXCLUDED.rejected_reviews,
            changes_requested = EXCLUDED.changes_requested,
            published_works = EXCLUDED.published_works,
            active_published_works = EXCLUDED.active_published_works,
            rights_attestations = EXCLUDED.rights_attestations,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, GOVERNANCE_VERSION, Timestamp.from(refreshed));
    updateHealth("publisher_workflow_daily", GOVERNANCE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.publisher_workflow_daily");
  }

  private void refreshRightsDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.rights_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.rights_daily(
            day, action, distribution_status, decision_count,
            calculation_version, refreshed_at, source_watermark)
        SELECT created_at::date, action, distribution_status, count(*), ?, ?, max(created_at)
        FROM catalog.rights_decision
        WHERE created_at >= current_date - INTERVAL '366 days'
        GROUP BY created_at::date, action, distribution_status
        ON CONFLICT (day, action, distribution_status) DO UPDATE SET
            decision_count = EXCLUDED.decision_count,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, GOVERNANCE_VERSION, Timestamp.from(refreshed));
    updateHealth("rights_daily", GOVERNANCE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.rights_daily");
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
