package com.bookrush.behaviorservice;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-cardinality reconciliation of the serving projection and attribution chain. */
@Component
public final class DataQualityReconciliation {
  private final JdbcTemplate jdbc;
  private final MeterRegistry metrics;
  private final AtomicLong events = new AtomicLong();
  private final AtomicLong projectedSubjects = new AtomicLong();
  private final AtomicLong attributed = new AtomicLong();
  private final AtomicLong orphaned = new AtomicLong();
  private final AtomicLong projectionAgeSeconds = new AtomicLong();

  public DataQualityReconciliation(JdbcTemplate jdbc, MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.metrics = metrics;
    Gauge.builder("bookrush.behavior.data_quality.events", events, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.data_quality.projected_subjects", projectedSubjects, AtomicLong::get)
        .register(metrics);
    Gauge.builder("bookrush.behavior.data_quality.attributed", attributed, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.data_quality.orphaned", orphaned, AtomicLong::get).register(metrics);
    Gauge.builder("bookrush.behavior.data_quality.projection_age_seconds", projectionAgeSeconds, AtomicLong::get)
        .register(metrics);
  }

  @Scheduled(fixedDelayString = "${bookrush.behavior.data-quality-delay-ms:300000}")
  public void scheduled() { reconcile(); }

  public Report reconcile() {
    long eventCount = count("SELECT count(*) FROM behavior.events");
    long subjectCount = count("SELECT count(*) FROM behavior.user_book_signal");
    long attributedCount = count("SELECT count(*) FROM behavior.outcome_attribution "
        + "WHERE recommendation_item_id IS NOT NULL OR impression_id IS NOT NULL");
    long orphanedCount = count("""
        SELECT count(*) FROM behavior.events e
        JOIN behavior.outcome_attribution a ON a.source_event_id=e.id
        WHERE (e.payload ? 'recommendationItemId' AND a.recommendation_item_id IS NULL)
           OR (e.payload ? 'impressionId' AND a.impression_id IS NULL)
        """);
    Instant latestEvent = jdbc.queryForObject("SELECT max(occurred_at) FROM behavior.events", Instant.class);
    Instant latestProjection = jdbc.queryForObject("SELECT max(updated_at) FROM behavior.user_book_signal", Instant.class);
    long age = latestEvent == null || latestProjection == null ? 0
        : Math.max(0, Duration.between(latestProjection, latestEvent).getSeconds());
    events.set(eventCount);
    projectedSubjects.set(subjectCount);
    attributed.set(attributedCount);
    orphaned.set(orphanedCount);
    projectionAgeSeconds.set(age);
    String outcome = orphanedCount == 0 ? "healthy" : "issues";
    metrics.counter("bookrush.behavior.data_quality.reconciliation", "outcome", outcome).increment();
    return new Report(eventCount, subjectCount, attributedCount, orphanedCount, age,
        orphanedCount == 0);
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0 : Math.max(0, value);
  }

  public record Report(long events, long projectedSubjects, long attributed,
      long orphaned, long projectionAgeSeconds, boolean healthy) { }
}
