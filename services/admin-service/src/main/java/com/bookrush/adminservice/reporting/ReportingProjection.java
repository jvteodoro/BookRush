package com.bookrush.adminservice.reporting;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
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
  private static final String VERSION = "events-issuer-subject-platform-windows-v3-untrusted-population";
  private static final String FEED_VERSION = "feed-attribution-v5-population-significant-read";
  private static final String PIPELINE_VERSION = "analytics-pipeline-v1";
  private static final String QUALITY_VERSION = "event-quality-v1";
  private static final String PRODUCT_VERSION = "product-activity-v1-untrusted-population";
  private static final String CONTENT_VERSION = "content-supply-v1";
  private static final String COVERAGE_VERSION = "content-coverage-v1";
  private static final String RANK_SCORE_VERSION = "content-rank-score-deciles-v1";
  private static final String EXCERPT_PRODUCTION_VERSION = "excerpt-production-v1";
  private static final String IMPORT_QUALITY_VERSION = "import-quality-v1";
  private static final String BACKLOG_VERSION = "backlog-freshness-v1";
  private static final String INGESTION_VERSION = "ingestion-operations-v1";
  private static final String INGESTION_QUEUE_VERSION = "ingestion-queue-v1";
  private static final String PHASE_VERSION = "analytics-phase-v1";
  private static final String READING_VERSION = "reading-session-duration-buckets-identity-v3-untrusted-population";
  private static final String RETENTION_VERSION = "retention-cohort-issuer-subject-v2-untrusted-population";
  private static final String READING_PROGRESS_VERSION = "reading-progress-cohort-v2-untrusted-population";
  private static final String READING_HABITS_VERSION = "reading-habits-v1-untrusted-population";
  private static final String LIBRARY_STATE_VERSION = "library-current-state-v1-untrusted-population";
  private static final String RECOMMENDATION_VERSION = "recommendation-ledger-v4-candidate-funnel-repeat-viewable-30d-untrusted-population";
  private static final String RECOMMENDATION_PROFILE_QUALITY_VERSION = "recommendation-profile-quality-v1-untrusted-population";
  private static final String RECOMMENDATION_QUALITY_VERSION = "recommendation-outcome-v3-exact-viewable-touch-untrusted-population";
  private static final String EVENT_QUALITY_VERSION = "event-quality-classified-events-v2";
  private static final String GOVERNANCE_VERSION = "governance-activity-v1-untrusted-population";
  private static final String MODERATION_VERSION = "moderation-aging-buckets-v2-untrusted-population";
  private static final String EDITORIAL_FUNNEL_VERSION = "editorial-submission-funnel-v1-untrusted-population";
  private static final String BOOK_ENGAGEMENT_VERSION = "book-engagement-v1-untrusted-population";
  private static final String EVENT_SCHEMA_VERSION = "event-schema-v1-untrusted-population";
  private static final String RECOMMENDATION_POSITION_VERSION = "recommendation-position-v3-exact-viewable-touch-untrusted-population";
  private static final String RECOMMENDATION_POSITION_FUNNEL_VERSION = "recommendation-position-funnel-v1-mature-viewable-ctr-untrusted-population";
  private static final String EDITORIAL_QUALITY_VERSION = "editorial-quality-v1-untrusted-population";
  private static final String EXCERPT_PERFORMANCE_VERSION = "excerpt-performance-v3-mature-viewable-ctr-untrusted-population";
  private static final String EXPERIMENT_VERSION = "feed-experiment-v3-mature-viewable-ctr-untrusted-population";
  private static final String ACCESS_AUDIT_VERSION = "access-audit-v1";
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final MeterRegistry metrics;
  private final ConcurrentHashMap<String, AtomicLong> projectionFreshness = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> projectionStatus = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> projectionRecords = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> ingestionQueueMetrics = new ConcurrentHashMap<>();

  public ReportingProjection(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
      MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${bookrush.reporting.refresh-delay-ms:300000}")
  public void refresh() {
    Instant refreshed = Instant.now();
    runProjection("reader_day", VERSION, () -> refreshReaderDay(refreshed));
    runProjection("feed_daily", FEED_VERSION, () -> refreshFeedDaily(refreshed));
    runProjection("pipeline_snapshot", PIPELINE_VERSION, () -> refreshPipelineSnapshot(refreshed));
    runProjection("attribution_daily", QUALITY_VERSION, () -> refreshAttributionDaily(refreshed));
    runProjection("reconciliation_daily", QUALITY_VERSION, () -> refreshReconciliationDaily(refreshed));
    runProjection("publisher_daily", PRODUCT_VERSION, () -> refreshPublisherDaily(refreshed));
    runProjection("content_supply_snapshot", CONTENT_VERSION, () -> refreshContentSupply(refreshed));
    runProjection("content_coverage_snapshot", COVERAGE_VERSION,
        () -> refreshContentCoverage(refreshed));
    runProjection("content_rank_score_snapshot", RANK_SCORE_VERSION,
        () -> refreshContentRankScores(refreshed));
    runProjection("excerpt_production_snapshot", EXCERPT_PRODUCTION_VERSION,
        () -> refreshExcerptProduction(refreshed));
    runProjection("import_quality_daily", IMPORT_QUALITY_VERSION,
        () -> refreshImportQuality(refreshed));
    runProjection("backlog_snapshot", BACKLOG_VERSION,
        () -> refreshBacklogSnapshot(refreshed));
    runProjection("ingestion_daily", INGESTION_VERSION, () -> refreshIngestionDaily(refreshed));
    runProjection("ingestion_queue_snapshot", INGESTION_QUEUE_VERSION,
        () -> refreshIngestionQueueSnapshot(refreshed));
    runProjection("pipeline_phase_daily", PHASE_VERSION, () -> refreshPipelinePhaseDaily(refreshed));
    runProjection("reading_daily", READING_VERSION, () -> refreshReadingDaily(refreshed));
    runProjection("reading_progress_daily", READING_PROGRESS_VERSION,
        () -> refreshReadingProgressDaily(refreshed));
    runProjection("reading_habits_daily", READING_HABITS_VERSION,
        () -> refreshReadingHabitsDaily(refreshed));
    runProjection("library_state_snapshot", LIBRARY_STATE_VERSION,
        () -> refreshLibraryStateSnapshot(refreshed));
    runProjection("retention_cohort", READING_VERSION, () -> refreshRetentionCohort(refreshed));
    runProjection("recommendation_daily", RECOMMENDATION_VERSION, () -> refreshRecommendationDaily(refreshed));
    runProjection("recommendation_profile_quality_daily", RECOMMENDATION_PROFILE_QUALITY_VERSION,
        () -> refreshRecommendationProfileQualityDaily(refreshed));
    runProjection("recommendation_quality_daily", RECOMMENDATION_QUALITY_VERSION,
        () -> refreshRecommendationQualityDaily(refreshed));
    runProjection("event_quality_daily", EVENT_QUALITY_VERSION, () -> refreshEventQualityDaily(refreshed));
    runProjection("moderation_daily", MODERATION_VERSION, () -> refreshModerationDaily(refreshed));
    runProjection("publisher_workflow_daily", GOVERNANCE_VERSION, () -> refreshPublisherWorkflowDaily(refreshed));
    runProjection("editorial_submission_funnel_daily", EDITORIAL_FUNNEL_VERSION,
        () -> refreshEditorialSubmissionFunnelDaily(refreshed));
    runProjection("rights_daily", GOVERNANCE_VERSION, () -> refreshRightsDaily(refreshed));
    runProjection("rights_status_daily", GOVERNANCE_VERSION,
        () -> refreshRightsStatusDaily(refreshed));
    runProjection("social_daily", PRODUCT_VERSION, () -> refreshSocialDaily(refreshed));
    runProjection("book_engagement_daily", BOOK_ENGAGEMENT_VERSION,
        () -> refreshBookEngagementDaily(refreshed));
    runProjection("event_schema_daily", EVENT_SCHEMA_VERSION,
        () -> refreshEventSchemaDaily(refreshed));
    runProjection("recommendation_position_daily", RECOMMENDATION_POSITION_VERSION,
        () -> refreshRecommendationPositionDaily(refreshed));
    runProjection("recommendation_position_funnel_daily", RECOMMENDATION_POSITION_FUNNEL_VERSION,
        () -> refreshRecommendationPositionFunnelDaily(refreshed));
    runProjection("editorial_quality_daily", EDITORIAL_QUALITY_VERSION,
        () -> refreshEditorialQualityDaily(refreshed));
    runProjection("excerpt_performance_daily", EXCERPT_PERFORMANCE_VERSION,
        () -> refreshExcerptPerformanceDaily(refreshed));
    runProjection("experiment_daily", EXPERIMENT_VERSION,
        () -> refreshExperimentDaily(refreshed));
    runProjection("access_audit_daily", ACCESS_AUDIT_VERSION,
        () -> refreshAccessAuditDaily(refreshed));
  }

  private void refreshReaderDay(Instant refreshed) {
    jdbc.update("""
          INSERT INTO reporting.reader_day(
              day, population, platform, active_users, newly_active_users, sessions,
              weekly_active_users, monthly_active_users, returning_users,
              active_seconds, book_opens, meaningful_reads, completions,
              calculation_version, refreshed_at, source_watermark)
          WITH scoped AS (
            SELECT e.occurred_at, e.identity_issuer, e.identity_subject,
              coalesce(e.identity_population, 'UNKNOWN') AS population,
              e.book_id, e.event_type, e.payload,
              CASE WHEN lower(e.payload->>'platform') IN ('web', 'mobile')
                THEN lower(e.payload->>'platform') ELSE 'UNKNOWN' END AS platform
            FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject IS NOT NULL
              AND e.event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                'BOOK_COMPLETE', 'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE',
                'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
              AND e.occurred_at >= current_date - INTERVAL '396 days'
          ), first_seen AS (
            SELECT e.identity_issuer, e.identity_subject,
              coalesce(e.identity_population, 'UNKNOWN') AS population,
              min(e.occurred_at::date) AS first_day
            FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject IS NOT NULL
              AND e.event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                'BOOK_COMPLETE', 'READ_COMPLETE', 'BOOK_LIKE', 'BOOK_UNLIKE',
                'BOOK_SAVE', 'BOOK_UNSAVE', 'BOOK_SHARE', 'BOOK_COMMENT')
            GROUP BY e.identity_issuer, e.identity_subject, coalesce(e.identity_population, 'UNKNOWN')
          ), daily AS (
            SELECT s.occurred_at::date AS day, s.population, s.platform,
                count(DISTINCT (s.identity_issuer, s.identity_subject)) AS active_users,
                count(DISTINCT (s.identity_issuer, s.identity_subject)) FILTER (WHERE f.first_day = s.occurred_at::date) AS newly_active_users,
                count(DISTINCT (s.identity_issuer, s.identity_subject)) FILTER (WHERE f.first_day < s.occurred_at::date) AS returning_users,
                count(*) FILTER (WHERE s.event_type = 'READ_SESSION') AS sessions,
                coalesce(sum(CASE WHEN s.event_type = 'READ_SESSION'
                    AND s.payload->>'activeSeconds' ~ '^[0-9]+$'
                  THEN LEAST((s.payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0) AS active_seconds,
                count(*) FILTER (WHERE s.event_type = 'BOOK_OPEN') AS book_opens,
                count(DISTINCT (s.identity_issuer, s.identity_subject, s.book_id)) FILTER (
                  WHERE s.event_type = 'READ_SESSION'
                    AND s.payload->>'activeSeconds' ~ '^[0-9]+$'
                    AND (s.payload->>'activeSeconds')::bigint >= 60) AS meaningful_reads,
                count(DISTINCT (s.identity_issuer, s.identity_subject, s.book_id)) FILTER (
                  WHERE s.event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
                max(s.occurred_at) AS source_watermark
            FROM scoped s JOIN first_seen f
              ON f.identity_issuer = s.identity_issuer
             AND f.identity_subject = s.identity_subject
             AND f.population = s.population
            GROUP BY s.occurred_at::date, s.population, s.platform
          )
          SELECT d.day, d.population, d.platform, d.active_users, d.newly_active_users, d.sessions,
              (SELECT count(DISTINCT (w.identity_issuer, w.identity_subject)) FROM scoped w
                WHERE w.occurred_at >= d.day - INTERVAL '6 days'
                  AND w.occurred_at < d.day + INTERVAL '1 day'
                  AND w.population = d.population
                  AND w.platform = d.platform),
              (SELECT count(DISTINCT (m.identity_issuer, m.identity_subject)) FROM scoped m
                WHERE m.occurred_at >= d.day - INTERVAL '29 days'
                  AND m.occurred_at < d.day + INTERVAL '1 day'
                  AND m.population = d.population
                  AND m.platform = d.platform),
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
          FROM reporting.reader_day WHERE platform = 'UNKNOWN'
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
      var health = jdbc.queryForMap("SELECT extract(epoch FROM last_successful_refresh)::bigint AS refreshed, "
          + "record_count FROM reporting.projection_health WHERE projection_name = ?", name);
      metric(projectionFreshness, "bookrush.reporting.projection.last_refresh_timestamp_seconds", name,
          "last refresh timestamp").set(number(health.get("refreshed")));
      metric(projectionRecords, "bookrush.reporting.projection.record_count", name, "materialized rows")
          .set(number(health.get("record_count")));
      metric(projectionStatus, "bookrush.reporting.projection.status", name, "1 on success, 0 on error").set(1);
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
      metric(projectionStatus, "bookrush.reporting.projection.status", name, "1 on success, 0 on error").set(0);
    }
  }

  private long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0;
  }

  private AtomicLong metric(ConcurrentHashMap<String, AtomicLong> values, String metricName,
      String projection, String description) {
    return values.computeIfAbsent(projection, ignored -> {
      AtomicLong value = new AtomicLong();
      Gauge.builder(metricName, value, AtomicLong::get)
          .description(description)
          .tag("projection", projection)
          .register(metrics);
      return value;
    });
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
          SELECT i.id AS impression_id, i.item_id, i.book_id, i.shown_at, i.viewable_at,
              i.viewable,
              r.model_version, r.strategy,
              COALESCE(population.identity_population, 'UNKNOWN') AS population,
              COALESCE(r.context->>'personalizationPath', 'UNKNOWN') AS feed_path,
              COALESCE(r.context->>'platform', 'UNKNOWN') AS platform,
              r.subject_key
          FROM recommendation.impression i
          JOIN recommendation.request r ON r.id = i.request_id
          LEFT JOIN LATERAL (
            SELECT e.identity_population FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject = r.subject_key
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          WHERE i.shown_at >= current_date - INTERVAL '366 days'
        ), outcome AS (
          SELECT oa.source_event_id, oa.subject_key, oa.impression_id, oa.recommendation_item_id,
              oa.exposure_event_id, exposure.payload->>'impressionId' AS exposure_impression_id,
              oa.outcome_type, e.payload, e.occurred_at
          FROM behavior.outcome_attribution oa
          JOIN behavior.events e ON e.id = oa.source_event_id
          LEFT JOIN behavior.events exposure ON exposure.id = oa.exposure_event_id
          WHERE oa.attribution_status = 'ATTRIBUTED'
        ), dwell_events AS (
          SELECT x.impression_id, x.item_id, d.id, d.event_type, d.occurred_at,
              COALESCE(NULLIF(d.payload->>'visitId', ''), d.id::text) AS visit_id,
              CASE WHEN d.payload->>'durationMs' ~ '^[0-9]{1,8}$'
                THEN (d.payload->>'durationMs')::bigint END AS duration_ms
          FROM exposure x
          JOIN behavior.events d
            ON d.identity_subject = x.subject_key
            AND d.book_id = x.book_id
            AND d.event_type IN ('DWELL', 'EXCERPT_DWELL', 'EXCERPT_SKIP')
            AND (d.payload->>'impressionId' = x.impression_id::text
              OR (d.payload->>'impressionId' IS NULL
                AND d.payload->>'recommendationItemId' = x.item_id::text))
            AND d.occurred_at >= COALESCE(x.viewable_at, x.shown_at)
            AND d.occurred_at < COALESCE(x.viewable_at, x.shown_at) + INTERVAL '1 day'
            AND ((d.event_type = 'EXCERPT_SKIP'
                AND d.payload->>'visitId' ~ '^[A-Za-z0-9._:-]{1,128}$')
              OR (d.event_type IN ('DWELL', 'EXCERPT_DWELL')
                AND d.payload->>'durationMs' ~ '^[0-9]{1,8}$'))
        ), dwell_events_by_visit AS (
          SELECT impression_id, item_id, visit_id,
              count(*) FILTER (WHERE event_type IN ('DWELL', 'EXCERPT_DWELL')
                AND duration_ms BETWEEN 1 AND 86400000) AS dwell_event_count,
              max(duration_ms) FILTER (WHERE event_type IN ('DWELL', 'EXCERPT_DWELL')
                AND duration_ms BETWEEN 1 AND 86400000) AS duration_ms,
              bool_or(event_type = 'EXCERPT_SKIP') AS skipped,
              max(occurred_at) AS dwell_watermark
          FROM dwell_events
          GROUP BY impression_id, item_id, visit_id
        ), dwell_by_exposure AS (
          SELECT impression_id, item_id,
              count(*) FILTER (WHERE dwell_event_count > 0) AS dwell_observations,
              coalesce(round(sum(coalesce(duration_ms, 0)) / 1000.0)::bigint, 0) AS dwell_seconds,
              count(*) FILTER (WHERE skipped) AS skipped_visits,
              max(dwell_watermark) AS dwell_watermark
          FROM dwell_events_by_visit
          GROUP BY impression_id, item_id
        ), funnel AS (
          SELECT x.day, x.model_version, x.strategy, x.feed_path, x.population, x.platform,
              count(DISTINCT x.impression_id) AS served_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.shown_at <= current_timestamp - INTERVAL '7 days') AS mature_served_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.viewable) AS viewable_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.viewable
                AND x.viewable_at IS NOT NULL
                AND x.viewable_at <= current_timestamp - INTERVAL '7 days') AS eligible_viewable_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.outcome_type = 'BOOK_OPEN') AS attributed_open_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.outcome_type = 'READ_SESSION'
                AND x.payload->>'activeSeconds' ~ '^[0-9]+$'
                AND (x.payload->>'activeSeconds')::bigint >= 60
                AND x.viewable AND x.viewable_at IS NOT NULL
                AND x.viewable_at <= current_timestamp - INTERVAL '7 days') AS mature_attributed_significant_read_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.outcome_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')
                AND x.viewable AND x.viewable_at IS NOT NULL
                AND x.viewable_at <= current_timestamp - INTERVAL '7 days') AS mature_attributed_complete_count,
              count(DISTINCT x.impression_id) FILTER (WHERE x.outcome_type = 'BOOK_OPEN'
                AND x.viewable
                AND x.viewable_at IS NOT NULL
                AND x.viewable_at <= current_timestamp - INTERVAL '7 days') AS mature_attributed_open_count,
              count(DISTINCT x.impression_id) FILTER (
                WHERE x.outcome_type = 'READ_PROGRESS' AND x.progress_percent >= 10)
                  AS attributed_read_10_count,
              count(DISTINCT x.impression_id) FILTER (
                WHERE x.outcome_type = 'READ_PROGRESS' AND x.progress_percent >= 50)
                  AS attributed_read_50_count,
              count(DISTINCT x.impression_id) FILTER (
                WHERE x.outcome_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS attributed_complete_count,
              max(x.source_watermark) AS source_watermark
          FROM (
            SELECT e.*, o.source_event_id, o.outcome_type, o.payload,
                CASE WHEN o.payload->>'percent' ~ '^(100(\\.0+)?|[0-9]{1,2}(\\.[0-9]{1,6})?)$'
                  THEN (o.payload->>'percent')::numeric END AS progress_percent,
                GREATEST(COALESCE(e.viewable_at, e.shown_at),
                  COALESCE(o.occurred_at, COALESCE(e.viewable_at, e.shown_at))) AS source_watermark,
                e.shown_at::date AS day
            FROM exposure e
            LEFT JOIN outcome o ON o.subject_key = e.subject_key
                AND e.viewable
                AND (o.impression_id = e.impression_id
                  OR (o.impression_id IS NULL AND o.recommendation_item_id = e.item_id
                    AND o.exposure_impression_id = e.impression_id::text))
                AND o.occurred_at >= COALESCE(e.viewable_at, e.shown_at)
                AND o.occurred_at < COALESCE(e.viewable_at, e.shown_at) + INTERVAL '7 days'
          ) x
          GROUP BY x.day, x.model_version, x.strategy, x.feed_path, x.population, x.platform
        ), dwell_rollup AS (
          SELECT e.shown_at::date AS day, e.model_version, e.strategy, e.feed_path, e.population, e.platform,
              coalesce(sum(d.dwell_observations), 0)::bigint AS dwell_observations,
              coalesce(sum(d.dwell_seconds), 0)::bigint AS dwell_seconds,
              coalesce(sum(d.skipped_visits), 0)::bigint AS skipped_visits,
              max(d.dwell_watermark) AS source_watermark
          FROM exposure e
          LEFT JOIN dwell_by_exposure d ON d.impression_id = e.impression_id
              AND d.item_id = e.item_id
          GROUP BY e.shown_at::date, e.model_version, e.strategy, e.feed_path, e.population, e.platform
        )
        INSERT INTO reporting.feed_daily(
            day, model_version, strategy, feed_path, population, platform,
            served_count, mature_served_count, viewable_count, eligible_viewable_count,
            mature_attributed_significant_read_count, mature_attributed_complete_count, attributed_open_count, mature_attributed_open_count,
            attributed_read_10_count, attributed_read_50_count,
            attributed_complete_count, dwell_observations, dwell_seconds, skipped_visits,
            calculation_version, refreshed_at, source_watermark)
        SELECT f.day, f.model_version, f.strategy, f.feed_path, f.population, f.platform,
            f.served_count, f.mature_served_count, f.viewable_count, f.eligible_viewable_count,
            f.mature_attributed_significant_read_count, f.mature_attributed_complete_count, f.attributed_open_count, f.mature_attributed_open_count,
            f.attributed_read_10_count, f.attributed_read_50_count,
            f.attributed_complete_count, d.dwell_observations, d.dwell_seconds,
            d.skipped_visits,
            ?, ?, GREATEST(f.source_watermark,
              coalesce(d.source_watermark, f.source_watermark))
        FROM funnel f
        LEFT JOIN dwell_rollup d ON d.day = f.day
            AND d.model_version = f.model_version
            AND d.strategy = f.strategy
            AND d.feed_path = f.feed_path
            AND d.population = f.population
            AND d.platform = f.platform
        ON CONFLICT (day, model_version, strategy, feed_path, population, platform) DO UPDATE SET
          served_count = EXCLUDED.served_count,
          mature_served_count = EXCLUDED.mature_served_count,
          mature_attributed_significant_read_count = EXCLUDED.mature_attributed_significant_read_count,
          mature_attributed_complete_count = EXCLUDED.mature_attributed_complete_count,
          viewable_count = EXCLUDED.viewable_count,
          eligible_viewable_count = EXCLUDED.eligible_viewable_count,
          attributed_open_count = EXCLUDED.attributed_open_count,
          mature_attributed_open_count = EXCLUDED.mature_attributed_open_count,
          attributed_read_10_count = EXCLUDED.attributed_read_10_count,
          attributed_read_50_count = EXCLUDED.attributed_read_50_count,
          attributed_complete_count = EXCLUDED.attributed_complete_count,
          dwell_observations = EXCLUDED.dwell_observations,
          dwell_seconds = EXCLUDED.dwell_seconds,
          skipped_visits = EXCLUDED.skipped_visits,
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
        ), behavior_events AS (
          SELECT occurred_at::date AS day,
              count(*) FILTER (WHERE event_type = 'BOOK_LIKE') AS behavior_like_events,
              count(*) FILTER (WHERE event_type = 'BOOK_UNLIKE') AS behavior_unlike_events,
              count(*) FILTER (WHERE event_type = 'BOOK_SAVE') AS behavior_save_events,
              count(*) FILTER (WHERE event_type = 'BOOK_UNSAVE') AS behavior_unsave_events,
              count(*) FILTER (WHERE event_type = 'BOOK_SHARE') AS behavior_share_events,
              count(*) FILTER (WHERE event_type = 'BOOK_COMMENT') AS behavior_comment_events,
              count(DISTINCT identity_subject) AS behavior_unique_actors,
              count(DISTINCT book_id) AS behavior_distinct_books,
              max(occurred_at) AS source_watermark
          FROM behavior.events
          WHERE event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE',
              'BOOK_SHARE', 'BOOK_COMMENT')
            AND occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
        ), days AS (
          SELECT day FROM activity UNION SELECT day FROM reports
          UNION SELECT day FROM moderation UNION SELECT day FROM behavior_events
        )
        INSERT INTO reporting.social_daily(
            day, follow_count, like_count, comment_count, share_count,
            book_opened_count, book_saved_count, unique_actors, reports_open,
            moderation_backlog, behavior_like_events, behavior_unlike_events,
            behavior_save_events, behavior_unsave_events, behavior_share_events,
            behavior_comment_events, behavior_unique_actors, behavior_distinct_books,
            calculation_version, refreshed_at, source_watermark)
        SELECT d.day, COALESCE(a.follow_count, 0),
            COALESCE(a.like_count, 0), COALESCE(a.comment_count, 0), COALESCE(a.share_count, 0),
            COALESCE(a.book_opened_count, 0), COALESCE(a.book_saved_count, 0),
            COALESCE(a.unique_actors, 0), COALESCE(r.reports_open, 0),
            COALESCE(m.moderation_backlog, 0), COALESCE(e.behavior_like_events, 0),
            COALESCE(e.behavior_unlike_events, 0), COALESCE(e.behavior_save_events, 0),
            COALESCE(e.behavior_unsave_events, 0), COALESCE(e.behavior_share_events, 0),
            COALESCE(e.behavior_comment_events, 0), COALESCE(e.behavior_unique_actors, 0),
            COALESCE(e.behavior_distinct_books, 0), ?, ?,
            GREATEST(a.source_watermark, r.source_watermark, m.source_watermark, e.source_watermark)
        FROM days d LEFT JOIN activity a ON a.day = d.day
          LEFT JOIN reports r ON r.day = d.day
          LEFT JOIN moderation m ON m.day = d.day
          LEFT JOIN behavior_events e ON e.day = d.day
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
            collected_at, active_books, reader_ready_books, excerpt_ready_books,
            available_asset_versions,
            analyzed_asset_versions, eligible_excerpts, ranked_excerpts,
            calculation_version, refreshed_at, source_watermark)
        WITH reader_ready_assets AS (
          SELECT DISTINCT b.id AS book_id, av.id AS asset_version_id
          FROM catalog.book b
          JOIN catalog.book_asset ba ON ba.book_id = b.id AND ba.status = 'ACTIVE'
          JOIN catalog.book_asset_version av
            ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
          LEFT JOIN catalog.license asset_license ON asset_license.id = ba.license_id
          LEFT JOIN catalog.edition ed ON ed.id = ba.edition_id
          LEFT JOIN catalog.license edition_license ON edition_license.id = ed.license_id
          WHERE b.status = 'ACTIVE'
            AND (
              CASE WHEN ba.license_id IS NOT NULL
                THEN asset_license.redistribution_allowed IS TRUE
                ELSE edition_license.redistribution_allowed IS TRUE END
              OR EXISTS (
                SELECT 1 FROM catalog.rights_decision rd
                WHERE rd.action = 'DISTRIBUTION'
                  AND rd.distribution_status = 'APPROVED'
                  AND rd.territory = 'GLOBAL'
                  AND (rd.asset_id = ba.id OR rd.edition_id = ba.edition_id)
                  AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                  AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp())
              )
            )
        ), excerpt_ready_books AS (
          SELECT DISTINCT rra.book_id
          FROM reader_ready_assets rra
          JOIN analytics.excerpt e ON e.source_asset_version_id = rra.asset_version_id
          WHERE e.body_eligible IS TRUE AND e.exclusion_reason IS NULL
        )
        SELECT date_trunc('minute', ?::timestamptz),
            (SELECT count(*) FROM catalog.book WHERE status = 'ACTIVE'),
            (SELECT count(DISTINCT book_id) FROM reader_ready_assets),
            (SELECT count(DISTINCT book_id) FROM excerpt_ready_books),
            (SELECT count(*) FROM catalog.book_asset_version WHERE status = 'AVAILABLE'),
            (SELECT count(DISTINCT input_asset_version_id)
               FROM analytics.analysis_run
              WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')),
            (SELECT count(*) FROM analytics.excerpt WHERE body_eligible),
            (SELECT count(*) FROM analytics.excerpt_rank), ?, ?,
            GREATEST(
              (SELECT max(updated_at) FROM catalog.book),
              (SELECT max(updated_at) FROM catalog.book_asset),
              (SELECT max(updated_at) FROM catalog.book_asset_version),
              (SELECT max(updated_at) FROM catalog.edition),
              (SELECT max(created_at) FROM analytics.excerpt_rank),
              (SELECT max(created_at) FROM analytics.excerpt),
              (SELECT max(updated_at) FROM catalog.license),
              (SELECT max(created_at) FROM catalog.rights_decision))
        ON CONFLICT (collected_at) DO UPDATE SET
            active_books = EXCLUDED.active_books,
            reader_ready_books = EXCLUDED.reader_ready_books,
            excerpt_ready_books = EXCLUDED.excerpt_ready_books,
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

  private void refreshContentCoverage(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.content_coverage_snapshot WHERE collected_at < now() - INTERVAL '30 days'");
    jdbc.update("""
        INSERT INTO reporting.content_coverage_snapshot(
            collected_at, language, rights_status, active_books,
            books_with_active_assets, available_asset_books, reader_ready_books,
            analyzed_books, eligible_excerpt_books, ranked_books,
            eligible_excerpts, ranked_excerpts, calculation_version,
            refreshed_at, source_watermark)
        WITH book_assets AS (
          SELECT b.id AS book_id, coalesce(nullif(b.original_language, ''), 'UNKNOWN') AS language,
            EXISTS (SELECT 1 FROM catalog.book_asset ba WHERE ba.book_id = b.id AND ba.status = 'ACTIVE')
              AS has_active_asset,
            EXISTS (
              SELECT 1 FROM catalog.book_asset ba
              JOIN catalog.book_asset_version av ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
              WHERE ba.book_id = b.id AND ba.status = 'ACTIVE'
            ) AS has_available_asset,
            EXISTS (
              SELECT 1 FROM catalog.book_asset ba
              LEFT JOIN catalog.license al ON al.id = ba.license_id
              LEFT JOIN catalog.edition ed ON ed.id = ba.edition_id
              LEFT JOIN catalog.license el ON el.id = ed.license_id
              JOIN catalog.book_asset_version av ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
              WHERE ba.book_id = b.id AND ba.status = 'ACTIVE'
                AND (CASE WHEN ba.license_id IS NOT NULL THEN al.redistribution_allowed IS TRUE
                          ELSE el.redistribution_allowed IS TRUE END
                  OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                    WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                      AND rd.territory = 'GLOBAL'
                      AND (rd.asset_id = ba.id OR rd.edition_id = ba.edition_id)
                      AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                      AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp())))
            ) AS reader_ready,
            EXISTS (
              SELECT 1 FROM catalog.book_asset ba
              JOIN catalog.book_asset_version av ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
              JOIN analytics.analysis_run ar ON ar.input_asset_version_id = av.id
                AND ar.status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
              WHERE ba.book_id = b.id AND ba.status = 'ACTIVE'
            ) AS analyzed
          FROM catalog.book b WHERE b.status = 'ACTIVE'
        ), eligible AS (
          SELECT b.id AS book_id, coalesce(nullif(b.original_language, ''), 'UNKNOWN') AS language,
            e.id AS excerpt_id,
            EXISTS (SELECT 1 FROM catalog.book_asset ba
              LEFT JOIN catalog.license al ON al.id = ba.license_id
              LEFT JOIN catalog.edition ed ON ed.id = ba.edition_id
              LEFT JOIN catalog.license el ON el.id = ed.license_id
              WHERE ba.book_id = b.id AND ba.status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM catalog.book_asset_version av
                  WHERE av.id = e.source_asset_version_id AND av.book_asset_id = ba.id AND av.status = 'AVAILABLE')
                AND (CASE WHEN ba.license_id IS NOT NULL THEN al.redistribution_allowed IS TRUE
                          ELSE el.redistribution_allowed IS TRUE END
                  OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                    WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                      AND rd.territory = 'GLOBAL'
                      AND (rd.asset_id = ba.id OR rd.edition_id = ba.edition_id)
                      AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                      AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp())))) AS reader_ready,
            EXISTS (SELECT 1 FROM analytics.excerpt_rank er WHERE er.excerpt_id = e.id) AS ranked
          FROM catalog.book b
          JOIN catalog.book_asset ba ON ba.book_id = b.id AND ba.status = 'ACTIVE'
          JOIN catalog.book_asset_version av ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
          JOIN analytics.excerpt e ON e.source_asset_version_id = av.id
          WHERE b.status = 'ACTIVE' AND e.body_eligible IS TRUE AND e.exclusion_reason IS NULL
        ), per_book AS (
          SELECT ba.book_id, ba.language, ba.has_active_asset, ba.has_available_asset,
            ba.reader_ready, ba.analyzed,
            EXISTS (SELECT 1 FROM eligible e WHERE e.book_id = ba.book_id AND e.reader_ready) AS has_eligible,
            EXISTS (SELECT 1 FROM eligible e WHERE e.book_id = ba.book_id AND e.reader_ready AND e.ranked) AS has_ranked,
            (SELECT count(*) FROM eligible e WHERE e.book_id = ba.book_id AND e.reader_ready) AS eligible_count,
            (SELECT count(*) FROM eligible e WHERE e.book_id = ba.book_id AND e.reader_ready AND e.ranked) AS ranked_count,
            EXISTS (SELECT 1 FROM catalog.book_asset ba2
              LEFT JOIN catalog.license al ON al.id = ba2.license_id
              LEFT JOIN catalog.edition ed ON ed.id = ba2.edition_id
              LEFT JOIN catalog.license el ON el.id = ed.license_id
              WHERE ba2.book_id = ba.book_id AND ba2.status = 'ACTIVE'
                AND (CASE WHEN ba2.license_id IS NOT NULL THEN al.redistribution_allowed IS TRUE
                          ELSE el.redistribution_allowed IS TRUE END
                  OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                    WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                      AND rd.territory = 'GLOBAL' AND (rd.asset_id = ba2.id OR rd.edition_id = ba2.edition_id)
                      AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                      AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp())))) AS rights_allowed
          FROM book_assets ba
        )
        SELECT date_trunc('minute', ?::timestamptz), language,
          CASE WHEN rights_allowed THEN 'RIGHTS_ALLOWED' ELSE 'RIGHTS_NOT_CONFIRMED' END,
          count(*), count(*) FILTER (WHERE has_active_asset), count(*) FILTER (WHERE has_available_asset),
          count(*) FILTER (WHERE reader_ready), count(*) FILTER (WHERE analyzed),
          count(*) FILTER (WHERE has_eligible), count(*) FILTER (WHERE has_ranked),
          sum(eligible_count), sum(ranked_count), ?, ?,
          GREATEST((SELECT max(updated_at) FROM catalog.book),
            (SELECT max(updated_at) FROM catalog.book_asset),
            (SELECT max(updated_at) FROM catalog.book_asset_version),
            (SELECT max(updated_at) FROM catalog.edition),
            (SELECT max(updated_at) FROM catalog.license),
            (SELECT max(created_at) FROM catalog.rights_decision),
            (SELECT max(created_at) FROM analytics.analysis_run),
            (SELECT max(created_at) FROM analytics.excerpt),
            (SELECT max(created_at) FROM analytics.excerpt_rank))
        FROM per_book GROUP BY language, rights_allowed
        ON CONFLICT (collected_at, language, rights_status) DO UPDATE SET
          active_books = EXCLUDED.active_books, books_with_active_assets = EXCLUDED.books_with_active_assets,
          available_asset_books = EXCLUDED.available_asset_books, reader_ready_books = EXCLUDED.reader_ready_books,
          analyzed_books = EXCLUDED.analyzed_books, eligible_excerpt_books = EXCLUDED.eligible_excerpt_books,
          ranked_books = EXCLUDED.ranked_books, eligible_excerpts = EXCLUDED.eligible_excerpts,
          ranked_excerpts = EXCLUDED.ranked_excerpts, calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at, source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), COVERAGE_VERSION, Timestamp.from(refreshed));
    updateHealth("content_coverage_snapshot", COVERAGE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.content_coverage_snapshot");
  }

  private void refreshContentRankScores(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.content_rank_score_snapshot WHERE collected_at < now() - INTERVAL '30 days'");
    jdbc.update("""
        INSERT INTO reporting.content_rank_score_snapshot(
            collected_at, language, rights_status, ranker_code, ranker_version,
            configuration_hash, score_decile, score_min, score_max, excerpt_count,
            calculation_version, refreshed_at, source_watermark)
        WITH eligible_scores AS (
          SELECT coalesce(nullif(b.original_language, ''), 'UNKNOWN') AS language,
            CASE WHEN rights_allowed THEN 'RIGHTS_ALLOWED' ELSE 'RIGHTS_NOT_CONFIRMED' END AS rights_status,
            er.ranker_code, er.ranker_version, er.configuration_hash, er.final_score,
            er.created_at AS rank_created_at, e.created_at AS excerpt_created_at,
            av.updated_at AS asset_version_updated_at, ba.updated_at AS asset_updated_at,
            b.updated_at AS book_updated_at
          FROM catalog.book b
          JOIN catalog.book_asset ba ON ba.book_id = b.id AND ba.status = 'ACTIVE'
          JOIN catalog.book_asset_version av ON av.book_asset_id = ba.id AND av.status = 'AVAILABLE'
          JOIN analytics.excerpt e ON e.source_asset_version_id = av.id
            AND e.body_eligible IS TRUE AND e.exclusion_reason IS NULL
          JOIN analytics.excerpt_rank er ON er.excerpt_id = e.id
          LEFT JOIN catalog.license al ON al.id = ba.license_id
          LEFT JOIN catalog.edition ed ON ed.id = ba.edition_id
          LEFT JOIN catalog.license el ON el.id = ed.license_id
          CROSS JOIN LATERAL (SELECT
            (CASE WHEN ba.license_id IS NOT NULL THEN al.redistribution_allowed IS TRUE
                  ELSE el.redistribution_allowed IS TRUE END
              OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                  AND rd.territory = 'GLOBAL'
                  AND (rd.asset_id = ba.id OR rd.edition_id = ba.edition_id)
                  AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                  AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp()))) AS rights_allowed
          ) rights
          WHERE b.status = 'ACTIVE' AND er.final_score > '-Infinity'::double precision
            AND er.final_score < 'Infinity'::double precision
        ), scored AS (
          SELECT *, min(final_score) OVER score_group AS score_min,
            max(final_score) OVER score_group AS score_max,
            CASE WHEN min(final_score) OVER score_group = max(final_score) OVER score_group THEN 0
              ELSE LEAST(9, GREATEST(0,
                width_bucket(final_score, min(final_score) OVER score_group,
                  max(final_score) OVER score_group, 10) - 1)) END AS score_decile
          FROM eligible_scores
          WINDOW score_group AS (PARTITION BY language, rights_status,
            ranker_code, ranker_version, configuration_hash)
        )
        SELECT date_trunc('minute', ?::timestamptz), language, rights_status,
          ranker_code, ranker_version, configuration_hash, score_decile,
          min(score_min), max(score_max), count(*), ?, ?,
          GREATEST((SELECT max(updated_at) FROM catalog.book),
            (SELECT max(updated_at) FROM catalog.book_asset),
            (SELECT max(updated_at) FROM catalog.book_asset_version),
            (SELECT max(updated_at) FROM catalog.edition),
            (SELECT max(updated_at) FROM catalog.license),
            (SELECT max(created_at) FROM catalog.rights_decision),
            (SELECT max(created_at) FROM analytics.excerpt),
            (SELECT max(created_at) FROM analytics.excerpt_rank))
        FROM scored
        GROUP BY language, rights_status, ranker_code, ranker_version,
          configuration_hash, score_decile
        ON CONFLICT (collected_at, language, rights_status, ranker_code,
          ranker_version, configuration_hash, score_decile) DO UPDATE SET
          score_min = EXCLUDED.score_min, score_max = EXCLUDED.score_max,
          excerpt_count = EXCLUDED.excerpt_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at, source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), RANK_SCORE_VERSION, Timestamp.from(refreshed));
    updateHealth("content_rank_score_snapshot", RANK_SCORE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.content_rank_score_snapshot");
  }

  private void refreshExcerptProduction(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.excerpt_production_snapshot WHERE collected_at < now() - INTERVAL '30 days'");
    jdbc.update("""
        INSERT INTO reporting.excerpt_production_snapshot(
            collected_at, language, rights_status, generation_method, outcome,
            reason_bucket, excerpt_count, calculation_version, refreshed_at,
            source_watermark)
        WITH ranks AS (
          SELECT excerpt_id,
            bool_or(exclusion_reason IS NULL) AS has_rank,
            min(exclusion_reason) FILTER (WHERE exclusion_reason IS NOT NULL) AS exclusion_reason
          FROM analytics.excerpt_rank GROUP BY excerpt_id
        ), source AS (
          SELECT coalesce(nullif(b.original_language, ''), 'UNKNOWN') AS language,
            CASE WHEN (CASE WHEN ba.license_id IS NOT NULL
                  THEN al.redistribution_allowed IS TRUE ELSE el.redistribution_allowed IS TRUE END)
                OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                  WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                    AND rd.territory = 'GLOBAL'
                    AND (rd.asset_id = ba.id OR rd.edition_id = ba.edition_id)
                    AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                    AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp()))
              THEN 'RIGHTS_ALLOWED' ELSE 'RIGHTS_NOT_CONFIRMED' END AS rights_status,
            e.generation_method,
            CASE WHEN NOT e.body_eligible OR e.exclusion_reason IS NOT NULL THEN 'STRUCTURALLY_EXCLUDED'
              WHEN r.has_rank THEN 'RANKED'
              WHEN r.exclusion_reason IS NOT NULL THEN 'RANKING_EXCLUDED'
              ELSE 'ELIGIBLE_UNRANKED' END AS outcome,
            CASE
              WHEN NOT e.body_eligible OR e.exclusion_reason IS NOT NULL THEN
                CASE e.exclusion_reason
                  WHEN 'MALFORMED_TEXT' THEN 'MALFORMED_TEXT'
                  WHEN 'BOILERPLATE' THEN 'BOILERPLATE'
                  WHEN 'TABLE_OF_CONTENTS' THEN 'TABLE_OF_CONTENTS'
                  WHEN 'HEADING_ONLY' THEN 'HEADING_ONLY'
                  ELSE 'OTHER' END
              WHEN r.exclusion_reason IN ('TOO_SHORT','TOO_LONG','AUTONOMY_BELOW_MINIMUM',
                'EMBEDDING_MODEL_MISMATCH','EMBEDDING_UNAVAILABLE','EMBEDDING_EMPTY',
                'EMBEDDING_DIMENSION_MISMATCH') THEN r.exclusion_reason
              WHEN r.exclusion_reason LIKE 'INVALID_FEATURE:%' THEN 'INVALID_FEATURE'
              WHEN r.exclusion_reason IS NOT NULL THEN 'OTHER'
              ELSE 'NONE' END AS reason_bucket,
            b.updated_at AS book_updated_at, ba.updated_at AS asset_updated_at,
            av.updated_at AS version_updated_at, e.created_at AS excerpt_created_at
          FROM analytics.excerpt e
          JOIN catalog.book_asset_version av ON av.id = e.source_asset_version_id
          JOIN catalog.book_asset ba ON ba.id = av.book_asset_id
          JOIN catalog.book b ON b.id = ba.book_id AND b.status = 'ACTIVE'
          LEFT JOIN catalog.license al ON al.id = ba.license_id
          LEFT JOIN catalog.edition ed ON ed.id = ba.edition_id
          LEFT JOIN catalog.license el ON el.id = ed.license_id
          LEFT JOIN ranks r ON r.excerpt_id = e.id
        )
        SELECT date_trunc('minute', ?::timestamptz), language, rights_status,
          coalesce(nullif(generation_method, ''), 'UNKNOWN'), outcome, reason_bucket,
          count(*), ?, ?, GREATEST((SELECT max(updated_at) FROM catalog.book),
            (SELECT max(updated_at) FROM catalog.book_asset),
            (SELECT max(updated_at) FROM catalog.book_asset_version),
            (SELECT max(updated_at) FROM catalog.edition),
            (SELECT max(updated_at) FROM catalog.license),
            (SELECT max(created_at) FROM catalog.rights_decision),
            (SELECT max(created_at) FROM analytics.excerpt),
            (SELECT max(created_at) FROM analytics.excerpt_rank))
        FROM source GROUP BY language, rights_status, generation_method, outcome, reason_bucket
        ON CONFLICT (collected_at, language, rights_status, generation_method,
          outcome, reason_bucket) DO UPDATE SET excerpt_count = EXCLUDED.excerpt_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at, source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), EXCERPT_PRODUCTION_VERSION, Timestamp.from(refreshed));
    updateHealth("excerpt_production_snapshot", EXCERPT_PRODUCTION_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.excerpt_production_snapshot");
  }

  private void refreshImportQuality(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.import_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.import_quality_daily(
            day, source_code, item_status, error_bucket, rights_outcome,
            asset_outcome, item_count, retry_attempt_count, calculation_version,
            refreshed_at, source_watermark)
        WITH classified AS (
          SELECT coalesce(i.finished_at, i.created_at)::date AS day, s.code AS source_code,
            i.status AS item_status,
            CASE WHEN i.error_code IS NULL THEN 'NONE'
              WHEN i.error_code LIKE 'RIGHTS_%' THEN 'RIGHTS'
              WHEN i.error_code LIKE 'ASSET_%' THEN 'ASSET'
              WHEN i.error_code LIKE 'SOURCE_%' THEN 'SOURCE'
              WHEN i.error_code LIKE 'VALIDATION_%' THEN 'VALIDATION'
              ELSE 'OTHER' END AS error_bucket,
            CASE WHEN i.book_id IS NULL AND i.edition_id IS NULL THEN 'NOT_ASSESSED'
              WHEN EXISTS (
                SELECT 1 FROM catalog.book_asset ba
                LEFT JOIN catalog.license al ON al.id = ba.license_id
                LEFT JOIN catalog.edition ed ON ed.id = coalesce(i.edition_id, ba.edition_id)
                LEFT JOIN catalog.license el ON el.id = ed.license_id
                WHERE (ba.book_id = i.book_id OR ba.edition_id = i.edition_id)
                  AND (CASE WHEN ba.license_id IS NOT NULL THEN al.redistribution_allowed IS TRUE
                            ELSE el.redistribution_allowed IS TRUE END
                    OR EXISTS (SELECT 1 FROM catalog.rights_decision rd
                      WHERE rd.action = 'DISTRIBUTION' AND rd.distribution_status = 'APPROVED'
                        AND rd.territory = 'GLOBAL'
                        AND (rd.asset_id = ba.id OR rd.edition_id = coalesce(i.edition_id, ba.edition_id))
                        AND (rd.valid_from IS NULL OR rd.valid_from <= clock_timestamp())
                        AND (rd.valid_until IS NULL OR rd.valid_until > clock_timestamp())))
              ) THEN 'ALLOWED' ELSE 'NOT_CONFIRMED' END AS rights_outcome,
            coalesce((SELECT av.status FROM catalog.book_asset_version av
              WHERE av.ingestion_item_id = i.id
              ORDER BY av.created_at DESC, av.id DESC LIMIT 1), 'NO_ASSET') AS asset_outcome,
            i.attempt_number
          FROM catalog.ingestion_item i
          JOIN catalog.ingestion_job j ON j.id = i.ingestion_job_id
          JOIN catalog.source s ON s.id = j.source_id
          WHERE coalesce(i.finished_at, i.created_at) >= current_date - INTERVAL '366 days'
        )
        SELECT day, source_code, item_status, error_bucket, rights_outcome, asset_outcome,
          count(*), count(*) FILTER (WHERE attempt_number > 1), ?, ?,
          GREATEST((SELECT max(updated_at) FROM catalog.ingestion_item),
            (SELECT max(updated_at) FROM catalog.ingestion_job),
            (SELECT max(created_at) FROM catalog.source_record),
            (SELECT max(updated_at) FROM catalog.book_asset_version),
            (SELECT max(created_at) FROM catalog.rights_decision))
        FROM classified
        GROUP BY day, source_code, item_status, error_bucket, rights_outcome, asset_outcome
        ON CONFLICT (day, source_code, item_status, error_bucket, rights_outcome, asset_outcome)
        DO UPDATE SET item_count = EXCLUDED.item_count,
          retry_attempt_count = EXCLUDED.retry_attempt_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at, source_watermark = EXCLUDED.source_watermark
        """, IMPORT_QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("import_quality_daily", IMPORT_QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.import_quality_daily");
  }

  private void refreshBacklogSnapshot(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.backlog_snapshot WHERE collected_at < now() - INTERVAL '30 days'");
    jdbc.update("""
        INSERT INTO reporting.backlog_snapshot(
            collected_at, source_type, queue_name, pending_count,
            oldest_pending_age_seconds, failed_count, stuck_count,
            last_successful_at, source_watermark, record_count, refresh_status,
            calculation_version, refreshed_at)
        WITH queues AS (
          SELECT 'INGESTION'::varchar AS source_type, 'catalog.ingestion_task'::varchar AS queue_name,
            count(*) FILTER (WHERE status IN ('PENDING','RETRY_WAIT')) AS pending_count,
            extract(epoch FROM (now() - min(created_at) FILTER (WHERE status IN ('PENDING','RETRY_WAIT'))))::bigint
              AS oldest_pending_age_seconds,
            count(*) FILTER (WHERE status = 'FAILED') AS failed_count,
            count(*) FILTER (WHERE status = 'RUNNING' AND lease_until < now()) AS stuck_count,
            NULL::timestamptz AS last_successful_at,
            max(updated_at) AS source_watermark, count(*) AS record_count,
            'AVAILABLE'::varchar AS refresh_status
          FROM catalog.ingestion_task
          UNION ALL
          SELECT 'ANALYTICS', 'analytics.analysis_job_item',
            count(*) FILTER (WHERE status = 'PENDING'),
            extract(epoch FROM (now() - min(created_at) FILTER (WHERE status = 'PENDING')))::bigint,
            count(*) FILTER (WHERE status = 'FAILED'),
            count(*) FILTER (WHERE status = 'RUNNING' AND started_at < now() - INTERVAL '15 minutes'),
            NULL::timestamptz, max(coalesce(finished_at, started_at, created_at)), count(*),
            'AVAILABLE'::varchar
          FROM analytics.analysis_job_item
          UNION ALL
          SELECT 'PROJECTION', projection_name,
            0, NULL::bigint, CASE WHEN refresh_status = 'ERROR' THEN 1 ELSE 0 END,
            0, last_successful_refresh, source_watermark, record_count, refresh_status
          FROM reporting.projection_health
        )
        SELECT date_trunc('minute', ?::timestamptz), source_type, queue_name,
          coalesce(pending_count, 0), oldest_pending_age_seconds,
          coalesce(failed_count, 0), coalesce(stuck_count, 0), last_successful_at,
          source_watermark, coalesce(record_count, 0), refresh_status, ?, ?
        FROM queues
        ON CONFLICT (collected_at, source_type, queue_name) DO UPDATE SET
          pending_count = EXCLUDED.pending_count,
          oldest_pending_age_seconds = EXCLUDED.oldest_pending_age_seconds,
          failed_count = EXCLUDED.failed_count, stuck_count = EXCLUDED.stuck_count,
          last_successful_at = EXCLUDED.last_successful_at,
          source_watermark = EXCLUDED.source_watermark, record_count = EXCLUDED.record_count,
          refresh_status = EXCLUDED.refresh_status,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at
        """, Timestamp.from(refreshed), BACKLOG_VERSION, Timestamp.from(refreshed));
    updateHealth("backlog_snapshot", BACKLOG_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.backlog_snapshot");
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

  private void refreshIngestionQueueSnapshot(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.ingestion_queue_snapshot WHERE collected_at < now() - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.ingestion_queue_snapshot(
            collected_at, queue_status, task_count, oldest_task_age_seconds,
            expired_lease_count, calculation_version, refreshed_at, source_watermark)
        WITH normalized_tasks AS (
          SELECT t.*,
              CASE
                WHEN t.status IN ('PENDING', 'RETRY_WAIT') THEN t.status
                WHEN t.status = 'RUNNING' THEN 'RUNNING'
                WHEN t.status = 'FAILED' THEN 'FAILED'
                WHEN t.status IN ('SUCCEEDED', 'NOOP', 'SKIPPED', 'QUARANTINED') THEN 'DONE'
                WHEN t.status = 'CANCELLED' THEN 'CANCELLED'
                ELSE 'OTHER'
              END AS queue_status
          FROM catalog.ingestion_task t
        )
        SELECT date_trunc('minute', ?::timestamptz), n.queue_status,
            count(*),
            CASE WHEN n.queue_status IN ('PENDING', 'RETRY_WAIT')
              THEN EXTRACT(EPOCH FROM (clock_timestamp() - min(n.created_at)))::bigint
              ELSE NULL END,
            count(*) FILTER (WHERE n.queue_status = 'RUNNING'
              AND n.lease_until < clock_timestamp()),
            ?, ?, max(coalesce(n.updated_at, n.created_at))
        FROM normalized_tasks n
        GROUP BY n.queue_status
        ON CONFLICT (collected_at, queue_status) DO UPDATE SET
            task_count = EXCLUDED.task_count,
            oldest_task_age_seconds = EXCLUDED.oldest_task_age_seconds,
            expired_lease_count = EXCLUDED.expired_lease_count,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, Timestamp.from(refreshed), INGESTION_QUEUE_VERSION, Timestamp.from(refreshed));
    var queueRows = jdbc.queryForList("""
        SELECT queue_status, task_count, oldest_task_age_seconds, expired_lease_count
        FROM reporting.ingestion_queue_snapshot
        WHERE collected_at = date_trunc('minute', ?::timestamptz)
        """, Timestamp.from(refreshed));
    var queueMetrics = new java.util.HashMap<String, Map<String, Object>>();
    queueRows.forEach(row -> queueMetrics.put(String.valueOf(row.get("queue_status")), row));
    for (String status : java.util.List.of(
        "PENDING", "RETRY_WAIT", "RUNNING", "FAILED", "DONE", "CANCELLED", "OTHER")) {
      var row = queueMetrics.get(status);
      ingestionQueueMetric("bookrush.ingestion.queue.tasks", status)
          .set(row == null ? 0 : number(row.get("task_count")));
      ingestionQueueMetric("bookrush.ingestion.queue.oldest_task_age_seconds", status)
          .set(row == null ? 0 : number(row.get("oldest_task_age_seconds")));
      ingestionQueueMetric("bookrush.ingestion.queue.expired_leases", status)
          .set(row == null ? 0 : number(row.get("expired_lease_count")));
    }
    updateHealth("ingestion_queue_snapshot", INGESTION_QUEUE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.ingestion_queue_snapshot");
  }

  private AtomicLong ingestionQueueMetric(String metricName, String status) {
    String key = metricName + ":" + status;
    return ingestionQueueMetrics.computeIfAbsent(key, ignored -> {
      AtomicLong value = new AtomicLong();
      Gauge.builder(metricName, value, AtomicLong::get)
          .description("Current ingestion queue snapshot from reporting projection")
          .tag("state", status)
          .register(metrics);
      return value;
    });
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
          SELECT occurred_at::date AS day, coalesce(identity_population, 'UNKNOWN') AS population,
              count(DISTINCT (identity_issuer, identity_subject)) AS unique_readers,
              count(DISTINCT (identity_issuer, identity_subject, book_id)) FILTER (WHERE event_type = 'BOOK_OPEN') AS books_opened,
              count(DISTINCT (identity_issuer, identity_subject, book_id)) FILTER (WHERE event_type = 'BOOK_OPEN') AS books_started,
              count(DISTINCT (identity_issuer, identity_subject, book_id)) FILTER (WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
              max(occurred_at) AS source_watermark
          FROM behavior.events
          WHERE identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
            AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS', 'BOOK_COMPLETE', 'READ_COMPLETE')
            AND occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date, coalesce(identity_population, 'UNKNOWN')
        ), sessions AS (
          SELECT started_at::date AS day, population.population, count(*) AS sessions,
              coalesce(sum(least(greatest(active_seconds, 0), 86400)), 0) AS active_seconds,
              percentile_cont(0.50) WITHIN GROUP (ORDER BY least(greatest(active_seconds, 0), 86400)) AS p50_session_seconds,
              percentile_cont(0.90) WITHIN GROUP (ORDER BY least(greatest(active_seconds, 0), 86400)) AS p90_session_seconds,
              count(*) FILTER (WHERE least(greatest(active_seconds, 0), 86400) < 60) AS session_under_1m,
              count(*) FILTER (WHERE least(greatest(active_seconds, 0), 86400) >= 60
                AND least(greatest(active_seconds, 0), 86400) < 300) AS session_1m_5m,
              count(*) FILTER (WHERE least(greatest(active_seconds, 0), 86400) >= 300
                AND least(greatest(active_seconds, 0), 86400) < 900) AS session_5m_15m,
              count(*) FILTER (WHERE least(greatest(active_seconds, 0), 86400) >= 900) AS session_15m_plus,
              max(coalesce(ended_at, started_at)) AS source_watermark
          FROM reader_state.session s
          LEFT JOIN LATERAL (
            SELECT coalesce(e.identity_population, 'UNKNOWN') AS population
            FROM behavior.events e WHERE e.identity_issuer IS NOT NULL
              AND e.identity_subject = s.subject_key AND e.occurred_at <= coalesce(s.ended_at, s.started_at)
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          WHERE started_at >= current_date - INTERVAL '366 days'
          GROUP BY started_at::date, population.population
        ), library AS (
          SELECT created_at::date AS day, population.population, count(DISTINCT book_id) AS saved_books,
              count(DISTINCT subject_key) AS unique_savers, max(created_at) AS source_watermark
          FROM reader_state.library l
          LEFT JOIN LATERAL (
            SELECT coalesce(e.identity_population, 'UNKNOWN') AS population
            FROM behavior.events e WHERE e.identity_issuer IS NOT NULL
              AND e.identity_subject = l.subject_key AND e.occurred_at <= l.created_at
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          WHERE status = 'SAVED' AND created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date, population.population
        )
        INSERT INTO reporting.reading_daily(
            day, population, unique_readers, sessions, active_seconds, p50_session_seconds,
            p90_session_seconds, session_under_1m, session_1m_5m,
            session_5m_15m, session_15m_plus, books_opened, books_started, completions,
            saved_books, unique_savers, calculation_version, refreshed_at, source_watermark)
        SELECT coalesce(e.day, s.day, l.day), coalesce(e.population, s.population, l.population), coalesce(e.unique_readers, 0),
            coalesce(s.sessions, 0), coalesce(s.active_seconds, 0),
            s.p50_session_seconds, s.p90_session_seconds,
            coalesce(s.session_under_1m, 0), coalesce(s.session_1m_5m, 0),
            coalesce(s.session_5m_15m, 0), coalesce(s.session_15m_plus, 0),
            coalesce(e.books_opened, 0), coalesce(e.books_started, 0), coalesce(e.completions, 0),
            coalesce(l.saved_books, 0), coalesce(l.unique_savers, 0), ?, ?,
            greatest(coalesce(e.source_watermark, '-infinity'::timestamptz),
              coalesce(s.source_watermark, '-infinity'::timestamptz),
              coalesce(l.source_watermark, '-infinity'::timestamptz))
        FROM events e FULL OUTER JOIN sessions s ON s.day = e.day AND s.population = e.population
          FULL OUTER JOIN library l ON l.day = coalesce(e.day, s.day) AND l.population = coalesce(e.population, s.population)
        ON CONFLICT (day, population) DO UPDATE SET
            unique_readers = EXCLUDED.unique_readers,
            sessions = EXCLUDED.sessions,
            active_seconds = EXCLUDED.active_seconds,
            p50_session_seconds = EXCLUDED.p50_session_seconds,
            p90_session_seconds = EXCLUDED.p90_session_seconds,
            session_under_1m = EXCLUDED.session_under_1m,
            session_1m_5m = EXCLUDED.session_1m_5m,
            session_5m_15m = EXCLUDED.session_5m_15m,
            session_15m_plus = EXCLUDED.session_15m_plus,
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

  private void refreshLibraryStateSnapshot(Instant refreshed) {
    jdbc.update("""
        INSERT INTO reporting.library_state_snapshot(
            snapshot_key, saved_book_pairs, saved_books, saved_subject_keys,
            calculation_version, refreshed_at, source_watermark)
        SELECT 'CURRENT', count(*), count(DISTINCT book_id), count(DISTINCT subject_key),
            ?, ?, NULL
        FROM reader_state.library
        WHERE status = 'SAVED'
        ON CONFLICT (snapshot_key) DO UPDATE SET
            saved_book_pairs = EXCLUDED.saved_book_pairs,
            saved_books = EXCLUDED.saved_books,
            saved_subject_keys = EXCLUDED.saved_subject_keys,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = NULL
        """, LIBRARY_STATE_VERSION, Timestamp.from(refreshed));
    updateHealth("library_state_snapshot", LIBRARY_STATE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.library_state_snapshot");
  }

  private void refreshRetentionCohort(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.retention_cohort WHERE cohort_day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH significant AS (
          SELECT identity_issuer, identity_subject, coalesce(identity_population, 'UNKNOWN') AS population, occurred_at::date AS activity_day
          FROM behavior.events
          WHERE identity_issuer IS NOT NULL AND identity_subject IS NOT NULL
            AND event_type = 'READ_SESSION'
            AND payload->>'activeSeconds' ~ '^[0-9]+$'
            AND (payload->>'activeSeconds')::bigint >= 60
        ), first_seen AS (
          SELECT identity_issuer, identity_subject, population, min(activity_day) AS cohort_day
          FROM significant GROUP BY identity_issuer, identity_subject, population
        ), cohorts AS (
          SELECT cohort_day, population, count(*) AS cohort_users
          FROM first_seen
          WHERE cohort_day >= current_date - INTERVAL '366 days'
          GROUP BY cohort_day, population
        ), offsets AS (
          SELECT c.cohort_day, c.population, o.offset_day, c.cohort_users,
              count(DISTINCT (s.identity_issuer, s.identity_subject)) AS retained_users
          FROM cohorts c CROSS JOIN (VALUES (0), (1), (7), (30)) o(offset_day)
          LEFT JOIN first_seen f ON f.cohort_day = c.cohort_day AND f.population = c.population
          LEFT JOIN significant s ON s.identity_issuer = f.identity_issuer
            AND s.identity_subject = f.identity_subject
            AND s.activity_day = c.cohort_day + o.offset_day
            AND s.population = c.population
          GROUP BY c.cohort_day, c.population, o.offset_day, c.cohort_users
        )
        INSERT INTO reporting.retention_cohort(
            cohort_day, offset_day, population, cohort_users, retained_users, mature,
            calculation_version, refreshed_at, source_watermark)
        SELECT cohort_day, offset_day, population, cohort_users, retained_users,
            current_date >= cohort_day + offset_day, ?, ?,
            (SELECT max(occurred_at) FROM behavior.events)
        FROM offsets
        ON CONFLICT (cohort_day, offset_day, population) DO UPDATE SET
            cohort_users = EXCLUDED.cohort_users,
            retained_users = EXCLUDED.retained_users,
            mature = EXCLUDED.mature,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, RETENTION_VERSION, Timestamp.from(refreshed));
    updateHealth("retention_cohort", RETENTION_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.retention_cohort");
  }

  private void refreshReadingProgressDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.reading_progress_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH progress_events AS (
          SELECT identity_subject, book_id, occurred_at,
              CASE WHEN payload->>'percent' ~ '^[0-9]+(\\.[0-9]+)?$'
                THEN LEAST((payload->>'percent')::numeric, 100) END AS percent
          FROM behavior.events
          WHERE event_type = 'READ_PROGRESS'
            AND identity_subject IS NOT NULL AND book_id IS NOT NULL
        ), progress_by_pair AS (
          SELECT identity_subject, book_id, min(occurred_at) AS first_progress_at,
              min(occurred_at) FILTER (WHERE percent >= 10) AS first_10_at,
              min(occurred_at) FILTER (WHERE percent >= 50) AS first_50_at,
              min(occurred_at) FILTER (WHERE percent >= 100) AS first_100_at,
              max(occurred_at) AS progress_watermark
          FROM progress_events
          GROUP BY identity_subject, book_id
        ), completions AS (
          SELECT identity_subject, book_id, min(occurred_at) AS first_complete_at,
              max(occurred_at) AS completion_watermark
          FROM behavior.events
          WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')
            AND identity_subject IS NOT NULL AND book_id IS NOT NULL
          GROUP BY identity_subject, book_id
        ), session_starts AS (
          SELECT subject_key AS identity_subject, book_id,
              min(started_at) AS first_session_at,
              max(coalesce(ended_at, started_at)) AS session_watermark
          FROM reader_state.session
          WHERE book_id IS NOT NULL
          GROUP BY subject_key, book_id
        ), pairs AS (
          SELECT coalesce(p.identity_subject, s.identity_subject) AS identity_subject,
              coalesce(p.book_id, s.book_id) AS book_id,
              coalesce(least(p.first_progress_at, s.first_session_at),
                p.first_progress_at, s.first_session_at) AS first_started_at,
              p.first_10_at, p.first_50_at,
              CASE WHEN c.first_complete_at >= coalesce(
                  least(p.first_progress_at, s.first_session_at),
                  p.first_progress_at, s.first_session_at)
                THEN least(p.first_100_at, c.first_complete_at)
                ELSE p.first_100_at END AS first_100_at,
              greatest(coalesce(p.progress_watermark, '-infinity'::timestamptz),
                coalesce(c.completion_watermark, '-infinity'::timestamptz),
                coalesce(s.session_watermark, '-infinity'::timestamptz)) AS source_watermark
          FROM progress_by_pair p
          FULL OUTER JOIN session_starts s
            ON s.identity_subject = p.identity_subject AND s.book_id = p.book_id
          LEFT JOIN completions c
            ON c.identity_subject = coalesce(p.identity_subject, s.identity_subject)
            AND c.book_id = coalesce(p.book_id, s.book_id)
        ), cohorts AS (
          SELECT first_started_at::date AS day,
              count(*) AS reader_book_pairs_started,
              count(*) FILTER (WHERE first_10_at IS NOT NULL) AS reader_book_pairs_10,
              count(*) FILTER (WHERE first_50_at IS NOT NULL) AS reader_book_pairs_50,
              count(*) FILTER (WHERE first_100_at IS NOT NULL) AS reader_book_pairs_100,
              max(source_watermark) AS source_watermark
          FROM pairs
          WHERE first_started_at >= current_date - INTERVAL '366 days'
          GROUP BY first_started_at::date
        ), library_actions AS (
          SELECT occurred_at::date AS day,
              count(*) FILTER (WHERE event_type = 'BOOK_SAVE') AS save_actions,
              count(*) FILTER (WHERE event_type = 'BOOK_UNSAVE') AS unsave_actions,
              count(DISTINCT identity_subject) FILTER (WHERE event_type = 'BOOK_SAVE') AS unique_savers,
              count(DISTINCT identity_subject) FILTER (WHERE event_type = 'BOOK_UNSAVE') AS unique_unsavers,
              max(occurred_at) AS source_watermark
          FROM behavior.events
          WHERE event_type IN ('BOOK_SAVE', 'BOOK_UNSAVE')
            AND identity_subject IS NOT NULL
            AND occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date
        ), days AS (
          SELECT day FROM cohorts UNION SELECT day FROM library_actions
        )
        INSERT INTO reporting.reading_progress_daily(
            day, reader_book_pairs_started, reader_book_pairs_10,
            reader_book_pairs_50, reader_book_pairs_100,
            save_actions, unsave_actions, unique_savers, unique_unsavers,
            calculation_version, refreshed_at, source_watermark)
        SELECT d.day, coalesce(c.reader_book_pairs_started, 0),
            coalesce(c.reader_book_pairs_10, 0), coalesce(c.reader_book_pairs_50, 0),
            coalesce(c.reader_book_pairs_100, 0), coalesce(l.save_actions, 0),
            coalesce(l.unsave_actions, 0), coalesce(l.unique_savers, 0),
            coalesce(l.unique_unsavers, 0), ?, ?,
            greatest(coalesce(c.source_watermark, '-infinity'::timestamptz),
              coalesce(l.source_watermark, '-infinity'::timestamptz))
        FROM days d
        LEFT JOIN cohorts c USING (day)
        LEFT JOIN library_actions l USING (day)
        ON CONFLICT (day) DO UPDATE SET
            reader_book_pairs_started = EXCLUDED.reader_book_pairs_started,
            reader_book_pairs_10 = EXCLUDED.reader_book_pairs_10,
            reader_book_pairs_50 = EXCLUDED.reader_book_pairs_50,
            reader_book_pairs_100 = EXCLUDED.reader_book_pairs_100,
            save_actions = EXCLUDED.save_actions,
            unsave_actions = EXCLUDED.unsave_actions,
            unique_savers = EXCLUDED.unique_savers,
            unique_unsavers = EXCLUDED.unique_unsavers,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, READING_PROGRESS_VERSION, Timestamp.from(refreshed));
    updateHealth("reading_progress_daily", READING_PROGRESS_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.reading_progress_daily");
  }

  private void refreshReadingHabitsDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.reading_habits_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH daily AS (
          SELECT subject_key, day, sum(greatest(minutes, 0)) AS reading_minutes,
              sum(greatest(books_completed, 0)) AS completed_books
          FROM reader_state.reading_day
          WHERE day >= current_date - INTERVAL '396 days'
            AND (minutes > 0 OR books_completed > 0)
          GROUP BY subject_key, day
        ), streaks AS (
          SELECT subject_key, day, reading_minutes, completed_books,
              day - (row_number() OVER (PARTITION BY subject_key ORDER BY day))::integer AS streak_group
          FROM daily
        ), streak_lengths AS (
          SELECT subject_key, day, reading_minutes, completed_books,
              count(*) OVER (PARTITION BY subject_key, streak_group
                ORDER BY day ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS streak_days
          FROM streaks
        )
        INSERT INTO reporting.reading_habits_daily(
            day, active_readers, reading_minutes, completed_books,
            streak_1_day_users, streak_2_6_day_users,
            streak_7_29_day_users, streak_30_plus_day_users,
            calculation_version, refreshed_at, source_watermark)
        SELECT day, count(*), sum(reading_minutes), sum(completed_books),
            count(*) FILTER (WHERE streak_days = 1),
            count(*) FILTER (WHERE streak_days BETWEEN 2 AND 6),
            count(*) FILTER (WHERE streak_days BETWEEN 7 AND 29),
            count(*) FILTER (WHERE streak_days >= 30), ?, ?, NULL
        FROM streak_lengths
        WHERE day >= current_date - INTERVAL '366 days'
        GROUP BY day
        ON CONFLICT (day) DO UPDATE SET
            active_readers = EXCLUDED.active_readers,
            reading_minutes = EXCLUDED.reading_minutes,
            completed_books = EXCLUDED.completed_books,
            streak_1_day_users = EXCLUDED.streak_1_day_users,
            streak_2_6_day_users = EXCLUDED.streak_2_6_day_users,
            streak_7_29_day_users = EXCLUDED.streak_7_29_day_users,
            streak_30_plus_day_users = EXCLUDED.streak_30_plus_day_users,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, READING_HABITS_VERSION, Timestamp.from(refreshed));
    updateHealth("reading_habits_daily", READING_HABITS_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.reading_habits_daily");
  }

  private void refreshRecommendationProfileQualityDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_profile_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH requests AS (
          SELECT r.created_at::date AS day, r.model_version, r.strategy,
            COALESCE(population.identity_population, 'UNKNOWN') AS population,
            COALESCE(r.context->>'personalizationPath', 'UNKNOWN') AS feed_path,
            COALESCE(r.context->>'platform', 'UNKNOWN') AS platform,
            COALESCE(r.context->>'profileConfidenceBucket', 'UNKNOWN') AS confidence_bucket,
            COALESCE(r.context->>'profileSizeBucket', 'UNKNOWN') AS profile_size_bucket,
            COALESCE(r.context->>'fatigueCoverageBucket', 'UNKNOWN') AS fatigue_coverage_bucket,
            COALESCE((r.context->>'coldStart') = 'true', false) AS cold_start,
            r.created_at AS source_watermark
          FROM recommendation.request r
          LEFT JOIN LATERAL (
            SELECT e.identity_population FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject = r.subject_key
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          WHERE r.created_at >= current_date - INTERVAL '366 days'
        )
        INSERT INTO reporting.recommendation_profile_quality_daily(
          day, model_version, strategy, feed_path, population, platform,
          confidence_bucket, profile_size_bucket, fatigue_coverage_bucket,
          cold_start, request_count, calculation_version, refreshed_at, source_watermark)
        SELECT day, model_version, strategy, feed_path, population, platform,
          confidence_bucket, profile_size_bucket, fatigue_coverage_bucket,
          cold_start, count(*), ?, ?, max(source_watermark)
        FROM requests
        GROUP BY day, model_version, strategy, feed_path, population, platform,
          confidence_bucket, profile_size_bucket, fatigue_coverage_bucket, cold_start
        ON CONFLICT (day, model_version, strategy, feed_path, population, platform,
          confidence_bucket, profile_size_bucket, fatigue_coverage_bucket, cold_start)
        DO UPDATE SET request_count = EXCLUDED.request_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at,
          source_watermark = EXCLUDED.source_watermark
        """, RECOMMENDATION_PROFILE_QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("recommendation_profile_quality_daily", RECOMMENDATION_PROFILE_QUALITY_VERSION,
        refreshed, "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.recommendation_profile_quality_daily");
  }

  private void refreshRecommendationDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH requests AS (
          SELECT r.id, r.subject_key, r.created_at::date AS day, r.model_version, r.strategy,
              r.candidate_count, r.scored_candidate_count, r.selected_count,
              COALESCE(population.identity_population, 'UNKNOWN') AS population,
              coalesce(r.context->>'personalizationPath', 'UNKNOWN') AS feed_path,
              coalesce(r.context->>'platform', 'UNKNOWN') AS platform,
              r.created_at AS source_watermark
          FROM recommendation.request r
          LEFT JOIN LATERAL (
            SELECT e.identity_population FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject = r.subject_key
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          WHERE r.created_at >= current_date - INTERVAL '366 days'
        ), items AS (
          SELECT i.request_id, count(*) AS item_count,
              max(i.created_at) AS source_watermark
          FROM recommendation.item i JOIN requests r ON r.id = i.request_id
          GROUP BY i.request_id
        ), item_distribution AS (
          SELECT r.day, r.model_version, r.strategy, r.feed_path, r.population, r.platform,
              count(DISTINCT i.book_id) AS unique_books,
              percentile_cont(0.50) WITHIN GROUP (ORDER BY i.rank) AS p50_rank,
              percentile_cont(0.90) WITHIN GROUP (ORDER BY i.rank) AS p90_rank
          FROM requests r JOIN recommendation.item i ON i.request_id = r.id
          GROUP BY r.day, r.model_version, r.strategy, r.feed_path, r.population, r.platform
        ), impression_events AS (
          SELECT r.day, r.model_version, r.strategy, r.feed_path, r.population, r.platform,
              i.id AS impression_id, i.book_id, i.viewable,
              COALESCE(i.viewable_at, i.shown_at) AS viewable_time,
              EXISTS (
                SELECT 1
                FROM recommendation.impression previous
                JOIN recommendation.request previous_request
                  ON previous_request.id = previous.request_id
                WHERE previous_request.subject_key = r.subject_key
                  AND previous.book_id = i.book_id
                  AND previous.viewable
                  AND COALESCE(previous.viewable_at, previous.shown_at)
                    >= COALESCE(i.viewable_at, i.shown_at) - INTERVAL '30 days'
                  AND (COALESCE(previous.viewable_at, previous.shown_at)
                    < COALESCE(i.viewable_at, i.shown_at)
                    OR (COALESCE(previous.viewable_at, previous.shown_at)
                      = COALESCE(i.viewable_at, i.shown_at) AND previous.id < i.id))
              ) AS repeat_viewable
          FROM recommendation.impression i JOIN requests r ON r.id = i.request_id
        ), impressions AS (
          SELECT day, model_version, strategy, feed_path, population, platform,
              count(DISTINCT impression_id) AS impression_count,
              count(DISTINCT impression_id) FILTER (WHERE viewable) AS viewable_count,
              count(DISTINCT impression_id) FILTER (WHERE viewable AND repeat_viewable)
                AS repeat_viewable_count,
              count(DISTINCT book_id) FILTER (WHERE viewable) AS viewable_unique_books,
              max(viewable_time) AS source_watermark
          FROM impression_events
          GROUP BY day, model_version, strategy, feed_path, population, platform
        )
        INSERT INTO reporting.recommendation_daily(
            day, model_version, strategy, feed_path, population, platform,
            request_count, candidate_count, scored_candidate_count, selected_count,
            item_count, impression_count, viewable_count,
            repeat_viewable_count, viewable_unique_books, unique_books, p50_rank, p90_rank,
            calculation_version, refreshed_at,
            source_watermark)
        SELECT r.day, r.model_version, r.strategy, r.feed_path, r.population, r.platform,
            count(*), CASE WHEN count(r.candidate_count) = count(*)
              THEN sum(r.candidate_count) END,
            CASE WHEN count(r.scored_candidate_count) = count(*)
              THEN sum(r.scored_candidate_count) END,
            CASE WHEN count(r.selected_count) = count(*) THEN sum(r.selected_count) END,
            coalesce(sum(i.item_count), 0), coalesce(max(m.impression_count), 0),
            coalesce(max(m.viewable_count), 0), coalesce(max(m.repeat_viewable_count), 0),
            coalesce(max(m.viewable_unique_books), 0), coalesce(max(b.unique_books), 0),
            max(b.p50_rank), max(b.p90_rank),
            ?, ?, greatest(max(r.source_watermark), max(i.source_watermark), max(m.source_watermark))
        FROM requests r LEFT JOIN items i ON i.request_id = r.id
          LEFT JOIN impressions m ON m.day = r.day
            AND m.model_version = r.model_version AND m.strategy = r.strategy
            AND m.feed_path = r.feed_path AND m.population = r.population AND m.platform = r.platform
          LEFT JOIN item_distribution b ON b.day = r.day
            AND b.model_version = r.model_version AND b.strategy = r.strategy
            AND b.feed_path = r.feed_path AND b.population = r.population AND b.platform = r.platform
        GROUP BY r.day, r.model_version, r.strategy, r.feed_path, r.population, r.platform
        ON CONFLICT (day, model_version, strategy, feed_path, population, platform) DO UPDATE SET
            request_count = EXCLUDED.request_count,
            candidate_count = EXCLUDED.candidate_count,
            scored_candidate_count = EXCLUDED.scored_candidate_count,
            selected_count = EXCLUDED.selected_count,
            item_count = EXCLUDED.item_count,
            impression_count = EXCLUDED.impression_count,
            viewable_count = EXCLUDED.viewable_count,
            repeat_viewable_count = EXCLUDED.repeat_viewable_count,
            viewable_unique_books = EXCLUDED.viewable_unique_books,
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

  private void refreshRecommendationQualityDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH source_outcome AS (
          SELECT oa.*, exposure.payload->>'impressionId' AS exposure_impression_id
          FROM behavior.outcome_attribution oa
          LEFT JOIN behavior.events exposure ON exposure.id = oa.exposure_event_id
          WHERE oa.attribution_status = 'ATTRIBUTED'
        ), attributed AS (
          SELECT oa.occurred_at::date AS day, r.model_version, r.strategy,
              oa.outcome_type, oa.subject_key, oa.book_id, i.rank,
              oa.occurred_at AS source_watermark
          FROM source_outcome oa
          JOIN recommendation.impression imp
            ON imp.id = oa.impression_id
            OR (oa.impression_id IS NULL AND imp.item_id = oa.recommendation_item_id
              AND oa.exposure_impression_id = imp.id::text)
          JOIN recommendation.item i ON i.id = imp.item_id
          JOIN recommendation.request r ON r.id = i.request_id
          WHERE imp.viewable
            AND (oa.recommendation_item_id IS NULL OR oa.recommendation_item_id = imp.item_id)
            AND oa.subject_key = r.subject_key
            AND oa.book_id = imp.book_id
            AND oa.occurred_at >= COALESCE(imp.viewable_at, imp.shown_at)
            AND oa.occurred_at < COALESCE(imp.viewable_at, imp.shown_at) + INTERVAL '7 days'
            AND oa.occurred_at >= current_date - INTERVAL '366 days'
        )
        INSERT INTO reporting.recommendation_quality_daily(
            day, model_version, strategy, outcome_type, attributed_count,
            unique_subjects, unique_books, p50_rank, calculation_version,
            refreshed_at, source_watermark)
        SELECT day, model_version, strategy, outcome_type, count(*),
            count(DISTINCT subject_key), count(DISTINCT book_id),
            percentile_cont(0.50) WITHIN GROUP (ORDER BY rank), ?, ?, max(source_watermark)
        FROM attributed
        GROUP BY day, model_version, strategy, outcome_type
        ON CONFLICT (day, model_version, strategy, outcome_type) DO UPDATE SET
            attributed_count = EXCLUDED.attributed_count,
            unique_subjects = EXCLUDED.unique_subjects,
            unique_books = EXCLUDED.unique_books,
            p50_rank = EXCLUDED.p50_rank,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, RECOMMENDATION_QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("recommendation_quality_daily", RECOMMENDATION_QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.recommendation_quality_daily");
  }

  private void refreshExcerptPerformanceDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.excerpt_performance_daily "
        + "WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH exposures AS (
          SELECT imp.id AS impression_id, imp.shown_at::date AS day,
              imp.viewable, imp.shown_at, imp.viewable_at, i.id AS item_id, i.excerpt_id,
              i.book_id, r.model_version, r.strategy, r.subject_key,
              e.body_eligible AS eligible, b.canonical_title AS book_title,
              b.original_language AS language
          FROM recommendation.impression imp
          JOIN recommendation.request r ON r.id = imp.request_id
          JOIN recommendation.item i ON i.id = imp.item_id
          LEFT JOIN analytics.excerpt e ON e.id = i.excerpt_id
          LEFT JOIN catalog.book_asset_version av ON av.id = e.source_asset_version_id
          LEFT JOIN catalog.book_asset ba ON ba.id = av.book_asset_id
          LEFT JOIN catalog.book b ON b.id = ba.book_id
          WHERE i.excerpt_id IS NOT NULL
            AND imp.shown_at >= current_date - INTERVAL '366 days'
        ), outcomes AS (
          SELECT x.impression_id, x.item_id, x.day, x.excerpt_id, x.book_id,
              x.book_title, x.language, x.model_version, x.strategy,
              x.eligible, x.subject_key, x.shown_at, x.viewable_at, x.viewable,
              oa.source_event_id, oa.outcome_type, oa.occurred_at
          FROM exposures x
          LEFT JOIN behavior.outcome_attribution oa
            ON (oa.impression_id = x.impression_id OR (oa.impression_id IS NULL
              AND oa.recommendation_item_id = x.item_id
              AND EXISTS (SELECT 1 FROM behavior.events exposure
                WHERE exposure.id = oa.exposure_event_id
                  AND exposure.payload->>'impressionId' = x.impression_id::text)))
           AND oa.attribution_status = 'ATTRIBUTED'
           AND (oa.recommendation_item_id IS NULL OR oa.recommendation_item_id = x.item_id)
           AND oa.subject_key = x.subject_key
           AND oa.book_id = x.book_id
           AND x.viewable
           AND oa.occurred_at >= COALESCE(x.viewable_at, x.shown_at)
           AND oa.occurred_at < COALESCE(x.viewable_at, x.shown_at) + INTERVAL '7 days'
        )
        INSERT INTO reporting.excerpt_performance_daily(
            day, excerpt_id, book_id, book_title, language, model_version, strategy,
            eligible, impressions, viewable_count, eligible_viewable_count,
            attributed_open_count, mature_attributed_open_count,
            attributed_read_count, attributed_complete_count, unique_subjects,
            calculation_version, refreshed_at, source_watermark)
        SELECT day, excerpt_id, book_id, max(book_title), max(language), model_version, strategy,
            coalesce(bool_and(coalesce(eligible, false)), false), count(DISTINCT impression_id),
            count(DISTINCT impression_id) FILTER (WHERE viewable),
            count(DISTINCT impression_id) FILTER (WHERE viewable AND viewable_at IS NOT NULL
              AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            count(DISTINCT source_event_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'
              AND viewable AND viewable_at IS NOT NULL
              AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            count(DISTINCT source_event_id) FILTER (WHERE outcome_type IN ('READ_SESSION', 'READ_PROGRESS')),
            count(DISTINCT source_event_id) FILTER (WHERE outcome_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')),
            count(DISTINCT subject_key), ?, ?, max(greatest(coalesce(viewable_at, shown_at),
                coalesce(occurred_at, coalesce(viewable_at, shown_at))))
        FROM outcomes
        GROUP BY day, excerpt_id, book_id, model_version, strategy
        ON CONFLICT (day, excerpt_id, model_version, strategy) DO UPDATE SET
            book_id = EXCLUDED.book_id,
            book_title = EXCLUDED.book_title,
            language = EXCLUDED.language,
            eligible = EXCLUDED.eligible,
            impressions = EXCLUDED.impressions,
            viewable_count = EXCLUDED.viewable_count,
            attributed_open_count = EXCLUDED.attributed_open_count,
            eligible_viewable_count = EXCLUDED.eligible_viewable_count,
            mature_attributed_open_count = EXCLUDED.mature_attributed_open_count,
            attributed_read_count = EXCLUDED.attributed_read_count,
            attributed_complete_count = EXCLUDED.attributed_complete_count,
            unique_subjects = EXCLUDED.unique_subjects,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EXCERPT_PERFORMANCE_VERSION, Timestamp.from(refreshed));
    updateHealth("excerpt_performance_daily", EXCERPT_PERFORMANCE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.excerpt_performance_daily");
  }

  private void refreshExperimentDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.experiment_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH assignments AS (
          SELECT experiment_id, assignment_version, variant, subject_key,
              assigned_at::date AS assignment_day, assigned_at AS source_watermark
          FROM recommendation.experiment_assignment
          WHERE assigned_at >= current_date - INTERVAL '366 days'
        ), requests AS (
          SELECT a.experiment_id, a.assignment_version, a.variant, a.subject_key,
              r.id AS request_id, r.created_at::date AS day, r.created_at AS source_watermark,
              a.source_watermark AS assignment_watermark
          FROM assignments a
          LEFT JOIN recommendation.request r
            ON r.subject_key = a.subject_key
           AND r.context->>'experimentId' = a.experiment_id
           AND r.context->>'experimentVariant' = a.variant
           AND r.context->>'assignmentVersion' = a.assignment_version
           AND r.created_at >= a.source_watermark
        ), facts AS (
          SELECT r.experiment_id, r.assignment_version, r.variant, r.subject_key,
              coalesce(r.day, a.assignment_day) AS day, r.request_id,
              r.source_watermark AS request_watermark, r.assignment_watermark,
              i.id AS impression_id,
              i.viewable, i.viewable_at, oa.source_event_id, oa.outcome_type, oa.occurred_at
          FROM requests r
          JOIN assignments a ON a.experiment_id = r.experiment_id
            AND a.assignment_version = r.assignment_version
            AND a.variant = r.variant AND a.subject_key = r.subject_key
          LEFT JOIN recommendation.impression i ON i.request_id = r.request_id
          LEFT JOIN behavior.outcome_attribution oa
            ON (oa.impression_id = i.id OR (oa.impression_id IS NULL
              AND oa.recommendation_item_id = i.item_id
              AND EXISTS (SELECT 1 FROM behavior.events exposure
                WHERE exposure.id = oa.exposure_event_id
                  AND exposure.payload->>'impressionId' = i.id::text)))
           AND oa.attribution_status = 'ATTRIBUTED'
           AND (oa.recommendation_item_id IS NULL OR oa.recommendation_item_id = i.item_id)
           AND oa.subject_key = r.subject_key
           AND oa.book_id = i.book_id
           AND i.viewable
           AND oa.occurred_at >= COALESCE(i.viewable_at, i.shown_at)
           AND oa.occurred_at < COALESCE(i.viewable_at, i.shown_at) + INTERVAL '7 days'
        )
        INSERT INTO reporting.experiment_daily(
            day, experiment_id, assignment_version, variant, assigned_subjects,
            request_count, impression_count, viewable_count, eligible_viewable_count,
            attributed_open_count, mature_attributed_open_count,
            attributed_read_count, attributed_complete_count, unique_subjects,
            calculation_version, refreshed_at, source_watermark)
        SELECT day, experiment_id, assignment_version, variant,
            count(DISTINCT subject_key), count(DISTINCT request_id),
            count(DISTINCT impression_id), count(DISTINCT impression_id) FILTER (WHERE viewable),
            count(DISTINCT impression_id) FILTER (WHERE viewable AND viewable_at IS NOT NULL
                AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'
                AND viewable AND viewable_at IS NOT NULL
                AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type IN ('READ_SESSION', 'READ_PROGRESS')),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')),
            count(DISTINCT subject_key) FILTER (WHERE request_id IS NOT NULL),
            ?, ?, max(greatest(coalesce(request_watermark, assignment_watermark),
                coalesce(occurred_at, request_watermark, assignment_watermark)))
        FROM facts
        GROUP BY day, experiment_id, assignment_version, variant
        ON CONFLICT (day, experiment_id, assignment_version, variant) DO UPDATE SET
            assigned_subjects = EXCLUDED.assigned_subjects,
            request_count = EXCLUDED.request_count,
            impression_count = EXCLUDED.impression_count,
            viewable_count = EXCLUDED.viewable_count,
            eligible_viewable_count = EXCLUDED.eligible_viewable_count,
            attributed_open_count = EXCLUDED.attributed_open_count,
            mature_attributed_open_count = EXCLUDED.mature_attributed_open_count,
            attributed_read_count = EXCLUDED.attributed_read_count,
            attributed_complete_count = EXCLUDED.attributed_complete_count,
            unique_subjects = EXCLUDED.unique_subjects,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EXPERIMENT_VERSION, Timestamp.from(refreshed));
    updateHealth("experiment_daily", EXPERIMENT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.experiment_daily");
  }

  private void refreshAccessAuditDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.access_audit_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.access_audit_daily(
            day, action, event_count, unique_actors, unique_targets,
            calculation_version, refreshed_at, source_watermark)
        SELECT created_at::date, action, count(*), count(DISTINCT actor_subject),
            count(DISTINCT NULLIF(target, '')), ?, ?, max(created_at)
        FROM admin.audit_log
        WHERE created_at >= current_date - INTERVAL '366 days'
        GROUP BY created_at::date, action
        ON CONFLICT (day, action) DO UPDATE SET
            event_count = EXCLUDED.event_count,
            unique_actors = EXCLUDED.unique_actors,
            unique_targets = EXCLUDED.unique_targets,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, ACCESS_AUDIT_VERSION, Timestamp.from(refreshed));
    updateHealth("access_audit_daily", ACCESS_AUDIT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.access_audit_daily");
  }

  private void refreshEventQualityDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.event_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH accepted AS (
          SELECT received_at::date AS day, count(*) AS accepted_events,
              max(received_at) AS source_watermark
          FROM behavior.events
          WHERE occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY received_at::date
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
            day, accepted_events, rejected_events, classified_events, duplicate_rejections,
            schema_rejections, payload_rejections, outbox_pending,
            outbox_published, outbox_failed_attempts,
            outbox_oldest_pending_age_seconds, calculation_version,
            refreshed_at, source_watermark)
        SELECT coalesce(a.day, r.day, o.day), coalesce(a.accepted_events, 0),
            coalesce(r.rejected_events, 0),
            coalesce(a.accepted_events, 0) + coalesce(r.rejected_events, 0),
            coalesce(r.duplicate_rejections, 0),
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
            classified_events = EXCLUDED.classified_events,
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
            open_under_1_day, open_1_to_3_days, open_3_to_7_days, open_7_days_or_more,
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
            extract(epoch FROM statement_timestamp() - min(created_at)
              FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')))::bigint,
            count(*) FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')
              AND statement_timestamp() - created_at < INTERVAL '1 day'),
            count(*) FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')
              AND statement_timestamp() - created_at >= INTERVAL '1 day'
              AND statement_timestamp() - created_at < INTERVAL '3 days'),
            count(*) FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')
              AND statement_timestamp() - created_at >= INTERVAL '3 days'
              AND statement_timestamp() - created_at < INTERVAL '7 days'),
            count(*) FILTER (WHERE status IN ('OPEN', 'IN_REVIEW')
              AND statement_timestamp() - created_at >= INTERVAL '7 days'),
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
            open_under_1_day = EXCLUDED.open_under_1_day,
            open_1_to_3_days = EXCLUDED.open_1_to_3_days,
            open_3_to_7_days = EXCLUDED.open_3_to_7_days,
            open_7_days_or_more = EXCLUDED.open_7_days_or_more,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, MODERATION_VERSION, Timestamp.from(refreshed));
    updateHealth("moderation_daily", MODERATION_VERSION, refreshed,
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

  private void refreshEditorialSubmissionFunnelDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.editorial_submission_funnel_daily "
        + "WHERE cohort_day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH review_state AS (
          SELECT submission_id,
              bool_or(status <> 'PENDING') AS reviewed,
              bool_or(status = 'APPROVED') AS approved,
              max(created_at) AS last_review_at
          FROM publisher.publication_review
          GROUP BY submission_id
        ), rights AS (
          SELECT submission_id, true AS attested, max(affirmed_at) AS last_attested_at
          FROM publisher.rights_attestation
          GROUP BY submission_id
        )
        INSERT INTO reporting.editorial_submission_funnel_daily(
            cohort_day, submissions, reviewed_submissions, approved_submissions,
            published_submissions, active_published_submissions,
            rights_attested_submissions, calculation_version, refreshed_at, source_watermark)
        SELECT s.created_at::date, count(*),
            count(*) FILTER (WHERE coalesce(r.reviewed, false)),
            count(*) FILTER (WHERE coalesce(r.approved, false)),
            count(*) FILTER (WHERE w.submission_id IS NOT NULL),
            count(*) FILTER (WHERE w.status = 'PUBLISHED'),
            count(*) FILTER (WHERE coalesce(rights.attested, false)),
            ?, ?, max(greatest(s.updated_at,
                coalesce(r.last_review_at, '-infinity'::timestamptz),
                coalesce(w.published_at, '-infinity'::timestamptz),
                coalesce(w.withdrawn_at, '-infinity'::timestamptz),
                coalesce(rights.last_attested_at, '-infinity'::timestamptz)))
        FROM publisher.submission s
        LEFT JOIN review_state r ON r.submission_id = s.id
        LEFT JOIN publisher.published_work w ON w.submission_id = s.id
        LEFT JOIN rights ON rights.submission_id = s.id
        WHERE s.created_at >= current_date - INTERVAL '366 days'
        GROUP BY s.created_at::date
        ON CONFLICT (cohort_day) DO UPDATE SET
            submissions = EXCLUDED.submissions,
            reviewed_submissions = EXCLUDED.reviewed_submissions,
            approved_submissions = EXCLUDED.approved_submissions,
            published_submissions = EXCLUDED.published_submissions,
            active_published_submissions = EXCLUDED.active_published_submissions,
            rights_attested_submissions = EXCLUDED.rights_attested_submissions,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EDITORIAL_FUNNEL_VERSION, Timestamp.from(refreshed));
    updateHealth("editorial_submission_funnel_daily", EDITORIAL_FUNNEL_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.editorial_submission_funnel_daily");
  }

  private void refreshBookEngagementDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.book_engagement_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH event_facts AS (
          SELECT occurred_at::date AS day, book_id, coalesce(identity_population, 'UNKNOWN') AS population,
              count(DISTINCT identity_subject) FILTER (WHERE identity_subject IS NOT NULL
                AND event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                  'BOOK_COMPLETE', 'READ_COMPLETE')) AS unique_readers,
              count(DISTINCT identity_subject) FILTER (WHERE event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
              count(*) FILTER (WHERE event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE',
                'BOOK_SHARE', 'BOOK_COMMENT')) AS interactions,
              count(*) FILTER (WHERE event_type = 'EXCERPT_IMPRESSION' AND payload->>'viewable' = 'true') AS viewable_count,
              coalesce(sum(CASE WHEN event_type = 'READ_SESSION'
                AND payload->>'activeSeconds' ~ '^[0-9]+$'
                THEN least((payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0) / 60 AS active_minutes,
              max(occurred_at) AS source_watermark
          FROM behavior.events
          WHERE book_id IS NOT NULL AND occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY occurred_at::date, book_id, coalesce(identity_population, 'UNKNOWN')
        ), books AS (
          SELECT db.day, db.book_id, e.population, b.canonical_title AS book_title,
              coalesce(b.original_language, 'und') AS language,
              db.impressions, db.opens, db.reads,
              coalesce(e.viewable_count, 0) AS viewable_count,
              coalesce(e.active_minutes, 0) AS active_minutes,
              coalesce(e.unique_readers, 0) AS unique_readers,
              coalesce(e.completions, 0) AS completions,
              coalesce(e.interactions, 0) AS interactions,
              greatest(db.day::timestamptz, coalesce(e.source_watermark, db.day::timestamptz)) AS source_watermark
          FROM behavior.daily_book db JOIN catalog.book b ON b.id = db.book_id
          LEFT JOIN event_facts e ON e.day = db.day AND e.book_id = db.book_id
          WHERE db.day >= current_date - INTERVAL '366 days' AND b.status = 'ACTIVE'
        ), language_events AS (
          SELECT e.occurred_at::date AS day, coalesce(e.identity_population, 'UNKNOWN') AS population, coalesce(b.original_language, 'und') AS language,
              count(DISTINCT e.identity_subject) FILTER (WHERE e.identity_subject IS NOT NULL
                AND e.event_type IN ('BOOK_OPEN', 'READ_SESSION', 'READ_PROGRESS',
                  'BOOK_COMPLETE', 'READ_COMPLETE')) AS unique_readers,
              count(DISTINCT e.identity_subject) FILTER (WHERE e.event_type IN ('BOOK_COMPLETE', 'READ_COMPLETE')) AS completions,
              count(*) FILTER (WHERE e.event_type IN ('BOOK_LIKE', 'BOOK_UNLIKE', 'BOOK_SAVE', 'BOOK_UNSAVE',
                'BOOK_SHARE', 'BOOK_COMMENT')) AS interactions,
              coalesce(sum(CASE WHEN e.event_type = 'READ_SESSION'
                AND e.payload->>'activeSeconds' ~ '^[0-9]+$'
                THEN least((e.payload->>'activeSeconds')::bigint, 86400) ELSE 0 END), 0) / 60 AS active_minutes,
              max(e.occurred_at) AS source_watermark
          FROM behavior.events e JOIN catalog.book b ON b.id = e.book_id
          WHERE e.book_id IS NOT NULL AND b.status = 'ACTIVE'
            AND e.occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY e.occurred_at::date, coalesce(e.identity_population, 'UNKNOWN'), coalesce(b.original_language, 'und')
        ), language_books AS (
          SELECT day, population, language, sum(impressions) AS impressions, sum(viewable_count) AS viewable_count,
              sum(opens) AS opens, sum(reads) AS reads, max(source_watermark) AS source_watermark
          FROM books GROUP BY day, population, language
        ), aggregated AS (
          SELECT day, population, 'BOOK' AS aggregation_level, book_id::text AS aggregation_key,
              book_id, book_title, language, impressions, viewable_count, opens, reads,
              active_minutes, unique_readers, completions, interactions, source_watermark
          FROM books
          UNION ALL
          SELECT lb.day, lb.population, 'LANGUAGE', lb.language, NULL, NULL, lb.language,
              lb.impressions, lb.viewable_count, lb.opens, lb.reads,
              coalesce(le.active_minutes, 0), coalesce(le.unique_readers, 0),
              coalesce(le.completions, 0), coalesce(le.interactions, 0),
              greatest(lb.source_watermark, coalesce(le.source_watermark, lb.source_watermark))
          FROM language_books lb LEFT JOIN language_events le
            ON le.day = lb.day AND le.population = lb.population AND le.language = lb.language
        )
        INSERT INTO reporting.book_engagement_daily(
            day, population, aggregation_level, aggregation_key, book_id, book_title, language,
            impressions, viewable_count, opens, reads, active_minutes, unique_readers,
            completions, interactions, calculation_version, refreshed_at, source_watermark)
        SELECT day, population, aggregation_level, aggregation_key, book_id, book_title, language,
            impressions, viewable_count, opens, reads, active_minutes, unique_readers,
            completions, interactions, ?, ?, source_watermark
        FROM aggregated
        ON CONFLICT (day, aggregation_level, aggregation_key, population) DO UPDATE SET
            book_id = EXCLUDED.book_id, book_title = EXCLUDED.book_title, language = EXCLUDED.language,
            impressions = EXCLUDED.impressions, viewable_count = EXCLUDED.viewable_count,
            opens = EXCLUDED.opens, reads = EXCLUDED.reads, active_minutes = EXCLUDED.active_minutes,
            unique_readers = EXCLUDED.unique_readers, completions = EXCLUDED.completions,
            interactions = EXCLUDED.interactions, calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at, source_watermark = EXCLUDED.source_watermark
        """, BOOK_ENGAGEMENT_VERSION, Timestamp.from(refreshed));
    updateHealth("book_engagement_daily", BOOK_ENGAGEMENT_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.book_engagement_daily");
  }

  private void refreshEventSchemaDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.event_schema_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH accepted AS (
          SELECT e.occurred_at::date AS day, e.event_type,
              coalesce(e.payload->>'schemaVersion', r.schema_version, 'UNVERSIONED') AS schema_version,
              coalesce(e.payload->>'clientVersion', 'UNKNOWN') AS client_version,
              count(*) AS record_count,
              count(*) FILTER (WHERE r.event_type IS NULL) AS unknown_event_count,
              max(e.received_at) AS source_watermark
          FROM behavior.events e LEFT JOIN behavior.schema_registry r ON r.event_type = e.event_type
          WHERE e.occurred_at >= current_date - INTERVAL '366 days'
          GROUP BY e.occurred_at::date, e.event_type,
              coalesce(e.payload->>'schemaVersion', r.schema_version, 'UNVERSIONED'),
              coalesce(e.payload->>'clientVersion', 'UNKNOWN')
        ), rejected AS (
          SELECT received_at::date AS day,
              coalesce(payload->>'eventType', 'UNKNOWN') AS event_type,
              coalesce(payload->>'schemaVersion', 'UNVERSIONED') AS schema_version,
              coalesce(payload->>'clientVersion', 'UNKNOWN') AS client_version,
              reason AS reject_reason, count(*) AS record_count,
              0::bigint AS unknown_event_count, max(received_at) AS source_watermark
          FROM behavior.rejects
          WHERE received_at >= current_date - INTERVAL '366 days'
          GROUP BY received_at::date, coalesce(payload->>'eventType', 'UNKNOWN'),
              coalesce(payload->>'schemaVersion', 'UNVERSIONED'),
              coalesce(payload->>'clientVersion', 'UNKNOWN'), reason
        ), rows AS (
          SELECT day, 'ACCEPTED' AS record_kind, event_type, schema_version,
              client_version, 'NONE'::varchar AS reject_reason, record_count,
              unknown_event_count, source_watermark FROM accepted
          UNION ALL
          SELECT day, 'REJECTED', event_type, schema_version, client_version,
              reject_reason, record_count, unknown_event_count, source_watermark FROM rejected
        )
        INSERT INTO reporting.event_schema_daily(
            day, record_kind, event_type, schema_version, client_version,
            reject_reason, record_count, unknown_event_count, calculation_version,
            refreshed_at, source_watermark)
        SELECT day, record_kind, event_type, schema_version, client_version,
            reject_reason, record_count, unknown_event_count, ?, ?, source_watermark
        FROM rows
        ON CONFLICT (day, record_kind, event_type, schema_version, client_version, reject_reason)
        DO UPDATE SET record_count = EXCLUDED.record_count,
            unknown_event_count = EXCLUDED.unknown_event_count,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EVENT_SCHEMA_VERSION, Timestamp.from(refreshed));
    updateHealth("event_schema_daily", EVENT_SCHEMA_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count FROM reporting.event_schema_daily");
  }

  private void refreshRecommendationPositionDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_position_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH source_outcome AS (
          SELECT oa.*, exposure.payload->>'impressionId' AS exposure_impression_id
          FROM behavior.outcome_attribution oa
          LEFT JOIN behavior.events exposure ON exposure.id = oa.exposure_event_id
          WHERE oa.attribution_status = 'ATTRIBUTED'
        ), attributed AS (
          SELECT oa.occurred_at::date AS day, r.model_version, r.strategy,
              CASE WHEN i.rank <= 3 THEN '1-3'
                   WHEN i.rank <= 10 THEN '4-10'
                   WHEN i.rank <= 24 THEN '11-24' ELSE '25+' END AS rank_bucket,
              oa.outcome_type, oa.subject_key, oa.book_id, oa.occurred_at AS source_watermark
          FROM source_outcome oa
          JOIN recommendation.impression imp
            ON imp.id = oa.impression_id
            OR (oa.impression_id IS NULL AND imp.item_id = oa.recommendation_item_id
              AND oa.exposure_impression_id = imp.id::text)
          JOIN recommendation.item i ON i.id = imp.item_id
          JOIN recommendation.request r ON r.id = i.request_id
          WHERE imp.viewable
            AND (oa.recommendation_item_id IS NULL OR oa.recommendation_item_id = imp.item_id)
            AND oa.subject_key = r.subject_key
            AND oa.book_id = imp.book_id
            AND oa.occurred_at >= COALESCE(imp.viewable_at, imp.shown_at)
            AND oa.occurred_at < COALESCE(imp.viewable_at, imp.shown_at) + INTERVAL '7 days'
            AND oa.occurred_at >= current_date - INTERVAL '366 days'
        )
        INSERT INTO reporting.recommendation_position_daily(
            day, model_version, strategy, rank_bucket, outcome_type,
            attributed_count, unique_subjects, unique_books,
            calculation_version, refreshed_at, source_watermark)
        SELECT day, model_version, strategy, rank_bucket, outcome_type,
            count(*), count(DISTINCT subject_key), count(DISTINCT book_id), ?, ?, max(source_watermark)
        FROM attributed
        GROUP BY day, model_version, strategy, rank_bucket, outcome_type
        ON CONFLICT (day, model_version, strategy, rank_bucket, outcome_type) DO UPDATE SET
            attributed_count = EXCLUDED.attributed_count,
            unique_subjects = EXCLUDED.unique_subjects,
            unique_books = EXCLUDED.unique_books,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, RECOMMENDATION_POSITION_VERSION, Timestamp.from(refreshed));
    updateHealth("recommendation_position_daily", RECOMMENDATION_POSITION_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.recommendation_position_daily");
  }

  private void refreshRecommendationPositionFunnelDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.recommendation_position_funnel_daily "
        + "WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH exposures AS (
          SELECT imp.id AS impression_id, imp.item_id, imp.book_id, imp.shown_at,
              imp.viewable_at, imp.viewable, r.subject_key, r.model_version, r.strategy,
              COALESCE(population.identity_population, 'UNKNOWN') AS population,
              COALESCE(r.context->>'platform', 'UNKNOWN') AS platform,
              CASE WHEN item.rank <= 3 THEN '1-3'
                   WHEN item.rank <= 10 THEN '4-10'
                   WHEN item.rank <= 24 THEN '11-24' ELSE '25+' END AS rank_bucket
          FROM recommendation.impression imp
          JOIN recommendation.request r ON r.id = imp.request_id
          LEFT JOIN LATERAL (
            SELECT e.identity_population FROM behavior.events e
            WHERE e.identity_issuer IS NOT NULL AND e.identity_subject = r.subject_key
            ORDER BY e.occurred_at DESC, e.id DESC LIMIT 1
          ) population ON true
          JOIN recommendation.item item ON item.id = imp.item_id
          WHERE imp.shown_at >= current_date - INTERVAL '366 days'
        ), outcomes AS (
          SELECT e.*, oa.outcome_type, oa.occurred_at
          FROM exposures e
          LEFT JOIN behavior.outcome_attribution oa
            ON (oa.impression_id = e.impression_id OR (oa.impression_id IS NULL
              AND oa.recommendation_item_id = e.item_id
              AND EXISTS (SELECT 1 FROM behavior.events exposure
                WHERE exposure.id = oa.exposure_event_id
                  AND exposure.payload->>'impressionId' = e.impression_id::text)))
           AND oa.attribution_status = 'ATTRIBUTED'
           AND (oa.recommendation_item_id IS NULL OR oa.recommendation_item_id = e.item_id)
           AND oa.subject_key = e.subject_key
           AND oa.book_id = e.book_id
           AND e.viewable
           AND oa.occurred_at >= COALESCE(e.viewable_at, e.shown_at)
           AND oa.occurred_at < COALESCE(e.viewable_at, e.shown_at) + INTERVAL '7 days'
        )
        INSERT INTO reporting.recommendation_position_funnel_daily(
            day, model_version, strategy, rank_bucket, population, platform,
            impression_count, viewable_count, eligible_viewable_count,
            attributed_open_count, mature_attributed_open_count,
            calculation_version, refreshed_at, source_watermark)
        SELECT shown_at::date, model_version, strategy, rank_bucket, population, platform,
            count(DISTINCT impression_id),
            count(DISTINCT impression_id) FILTER (WHERE viewable),
            count(DISTINCT impression_id) FILTER (WHERE viewable AND viewable_at IS NOT NULL
              AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'),
            count(DISTINCT impression_id) FILTER (WHERE outcome_type = 'BOOK_OPEN'
              AND viewable AND viewable_at IS NOT NULL
              AND viewable_at <= current_timestamp - INTERVAL '7 days'),
            ?, ?, max(greatest(COALESCE(viewable_at, shown_at),
              COALESCE(occurred_at, COALESCE(viewable_at, shown_at))))
        FROM outcomes
        GROUP BY shown_at::date, model_version, strategy, rank_bucket, population, platform
        ON CONFLICT (day, model_version, strategy, rank_bucket, population, platform) DO UPDATE SET
          impression_count = EXCLUDED.impression_count,
          viewable_count = EXCLUDED.viewable_count,
          eligible_viewable_count = EXCLUDED.eligible_viewable_count,
          attributed_open_count = EXCLUDED.attributed_open_count,
          mature_attributed_open_count = EXCLUDED.mature_attributed_open_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at,
          source_watermark = EXCLUDED.source_watermark
        """, RECOMMENDATION_POSITION_FUNNEL_VERSION, Timestamp.from(refreshed));
    updateHealth("recommendation_position_funnel_daily", RECOMMENDATION_POSITION_FUNNEL_VERSION,
        refreshed, "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.recommendation_position_funnel_daily");
  }

  private void refreshEditorialQualityDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.editorial_quality_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        WITH campaigns AS (
          SELECT created_at::date AS day, count(*) AS campaigns, max(created_at) AS source_watermark
          FROM analytics.annotation_campaign
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), items AS (
          SELECT created_at::date AS day,
              count(*) AS sampled_items,
              count(*) FILTER (WHERE state = 'PENDING') AS pending_items,
              count(*) FILTER (WHERE state = 'IN_PROGRESS') AS in_progress_items,
              count(*) FILTER (WHERE state = 'COMPLETE') AS completed_items,
              max(created_at) AS source_watermark
          FROM analytics.annotation_campaign_item
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), annotations AS (
          SELECT created_at::date AS day,
              count(*) AS annotations,
              count(*) FILTER (WHERE status = 'SUBMITTED') AS submitted_annotations,
              count(DISTINCT annotator_subject) AS unique_annotators,
              percentile_cont(0.50) WITHIN GROUP (ORDER BY elapsed_ms)
                FILTER (WHERE elapsed_ms IS NOT NULL) AS p50_elapsed_ms,
              percentile_cont(0.95) WITHIN GROUP (ORDER BY elapsed_ms)
                FILTER (WHERE elapsed_ms IS NOT NULL) AS p95_elapsed_ms,
              max(coalesce(submitted_at, updated_at, created_at)) AS source_watermark
          FROM analytics.annotation
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), disputed AS (
          SELECT day, count(*) AS disputed_items, max(source_watermark) AS source_watermark
          FROM (
            SELECT i.created_at::date AS day, i.id, max(i.created_at) AS source_watermark
            FROM analytics.annotation_campaign_item i
            JOIN analytics.annotation_campaign c ON c.id = i.campaign_id
            JOIN analytics.annotation a ON a.campaign_item_id = i.id
            JOIN analytics.annotation_dimension_value d ON d.annotation_id = a.id
            WHERE i.created_at >= current_date - INTERVAL '366 days'
            GROUP BY i.created_at::date, i.id, c.disagreement_threshold
            HAVING max(d.ordinal_value) - min(d.ordinal_value) >= c.disagreement_threshold
          ) q
          GROUP BY day
        ), adjudications AS (
          SELECT created_at::date AS day, count(*) AS adjudications, max(created_at) AS source_watermark
          FROM analytics.annotation_adjudication
          WHERE created_at >= current_date - INTERVAL '366 days'
          GROUP BY created_at::date
        ), days AS (
          SELECT day FROM campaigns UNION SELECT day FROM items UNION SELECT day FROM annotations
          UNION SELECT day FROM disputed UNION SELECT day FROM adjudications
        )
        INSERT INTO reporting.editorial_quality_daily(
            day, campaigns, sampled_items, pending_items, in_progress_items, completed_items,
            annotations, submitted_annotations, unique_annotators, disputed_items, adjudications,
            p50_elapsed_ms, p95_elapsed_ms, calculation_version, refreshed_at, source_watermark)
        SELECT d.day,
            coalesce(c.campaigns, 0), coalesce(i.sampled_items, 0), coalesce(i.pending_items, 0),
            coalesce(i.in_progress_items, 0), coalesce(i.completed_items, 0),
            coalesce(a.annotations, 0), coalesce(a.submitted_annotations, 0),
            coalesce(a.unique_annotators, 0), coalesce(x.disputed_items, 0), coalesce(ad.adjudications, 0),
            a.p50_elapsed_ms, a.p95_elapsed_ms, ?, ?,
            greatest(coalesce(c.source_watermark, '-infinity'::timestamptz),
              coalesce(i.source_watermark, '-infinity'::timestamptz),
              coalesce(a.source_watermark, '-infinity'::timestamptz),
              coalesce(x.source_watermark, '-infinity'::timestamptz),
              coalesce(ad.source_watermark, '-infinity'::timestamptz))
        FROM days d
        LEFT JOIN campaigns c ON c.day = d.day
        LEFT JOIN items i ON i.day = d.day
        LEFT JOIN annotations a ON a.day = d.day
        LEFT JOIN disputed x ON x.day = d.day
        LEFT JOIN adjudications ad ON ad.day = d.day
        ON CONFLICT (day) DO UPDATE SET
            campaigns = EXCLUDED.campaigns,
            sampled_items = EXCLUDED.sampled_items,
            pending_items = EXCLUDED.pending_items,
            in_progress_items = EXCLUDED.in_progress_items,
            completed_items = EXCLUDED.completed_items,
            annotations = EXCLUDED.annotations,
            submitted_annotations = EXCLUDED.submitted_annotations,
            unique_annotators = EXCLUDED.unique_annotators,
            disputed_items = EXCLUDED.disputed_items,
            adjudications = EXCLUDED.adjudications,
            p50_elapsed_ms = EXCLUDED.p50_elapsed_ms,
            p95_elapsed_ms = EXCLUDED.p95_elapsed_ms,
            calculation_version = EXCLUDED.calculation_version,
            refreshed_at = EXCLUDED.refreshed_at,
            source_watermark = EXCLUDED.source_watermark
        """, EDITORIAL_QUALITY_VERSION, Timestamp.from(refreshed));
    updateHealth("editorial_quality_daily", EDITORIAL_QUALITY_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.editorial_quality_daily");
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

  private void refreshRightsStatusDaily(Instant refreshed) {
    jdbc.update("DELETE FROM reporting.rights_status_daily WHERE day >= current_date - INTERVAL '366 days'");
    jdbc.update("""
        INSERT INTO reporting.rights_status_daily(
            day, source_type, right_type, territory, status, item_count,
            calculation_version, refreshed_at, source_watermark)
        SELECT day, source_type, right_type, territory, status, count(*), ?, ?, max(source_watermark)
        FROM (
          SELECT created_at::date AS day, 'CATALOG_DECISION'::varchar AS source_type,
            action::varchar AS right_type, territory,
            CASE
              WHEN distribution_status = 'REVOKED' THEN 'REVOKED'
              WHEN distribution_status = 'APPROVED' AND valid_until IS NOT NULL
                AND valid_until <= clock_timestamp() THEN 'EXPIRED'
              WHEN distribution_status = 'APPROVED' AND valid_from IS NOT NULL
                AND valid_from > clock_timestamp() THEN 'NOT_YET_VALID'
              WHEN distribution_status = 'APPROVED' THEN 'ACTIVE'
              ELSE distribution_status
            END::varchar AS status,
            created_at AS source_watermark
          FROM catalog.rights_decision
          WHERE created_at >= current_date - INTERVAL '366 days'
          UNION ALL
          SELECT affirmed_at::date, 'PUBLISHER_ATTESTATION'::varchar,
            right_type::varchar, territory, 'ATTESTED'::varchar, affirmed_at
          FROM publisher.rights_attestation
          WHERE affirmed_at >= current_date - INTERVAL '366 days'
        ) events
        GROUP BY day, source_type, right_type, territory, status
        ON CONFLICT (day, source_type, right_type, territory, status) DO UPDATE SET
          item_count = EXCLUDED.item_count,
          calculation_version = EXCLUDED.calculation_version,
          refreshed_at = EXCLUDED.refreshed_at,
          source_watermark = EXCLUDED.source_watermark
        """, GOVERNANCE_VERSION, Timestamp.from(refreshed));
    updateHealth("rights_status_daily", GOVERNANCE_VERSION, refreshed,
        "SELECT max(source_watermark) AS source_watermark, count(*) AS record_count "
            + "FROM reporting.rights_status_daily");
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
