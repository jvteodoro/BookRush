package com.bookrush.analytics.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Low-cardinality metrics for pipeline health; book IDs and hashes are never metric tags. */
@Component
public final class AnalyticsMetrics {
  private final Counter jobsCreated;
  private final Counter jobsReplayed;
  private final Counter jobsConflicted;
  private final Counter excerptsListed;
  private final Counter analysisFailures;
  private final Counter generatedExcerpts;
  private final Counter bookScoresBatches;
  private final Timer analyzerDuration;
  private final MeterRegistry registry;

  public AnalyticsMetrics(MeterRegistry registry) {
    this.registry = registry;
    jobsCreated = registry.counter("bookrush.analytics.jobs", "outcome", "created");
    jobsReplayed = registry.counter("bookrush.analytics.jobs", "outcome", "replayed");
    jobsConflicted = registry.counter("bookrush.analytics.jobs", "outcome", "conflict");
    excerptsListed = registry.counter("bookrush.analytics.excerpts", "operation", "listed");
    analysisFailures = registry.counter("bookrush.analytics.failures", "stage", "analysis");
    generatedExcerpts = registry.counter("bookrush.analytics.excerpts", "operation", "generated");
    bookScoresBatches = registry.counter("bookrush.analytics.book_scores", "operation", "batch");
    analyzerDuration = registry.timer("bookrush.analytics.analyzer.duration");
  }

  public void jobCreated() {
    jobsCreated.increment();
  }

  public void jobReplayed() {
    jobsReplayed.increment();
  }

  public void jobConflict() {
    jobsConflicted.increment();
  }

  public void excerptsListed(int count) {
    excerptsListed.increment(Math.max(0, count));
  }

  public void analysisFailure() {
    analysisFailures.increment();
  }

  public void excerptsGenerated(int count) {
    generatedExcerpts.increment(Math.max(0, count));
  }

  public void bookScoresBatch() {
    bookScoresBatches.increment();
  }

  public void bookScoresBatch(int requested, int returned, String strategy, String jit) {
    bookScoresBatches.increment();
    registry.counter("bookrush.analytics.book_scores.requested", "strategy", strategy).increment(requested);
    registry.counter("bookrush.analytics.book_scores.returned", "strategy", strategy).increment(returned);
    registry.counter("bookrush.analytics.book_scores.jit", "mode", jit).increment();
  }

  public Timer.Sample startBookScoresEndpoint() {
    return Timer.start(registry);
  }

  public void stopBookScoresEndpoint(Timer.Sample sample) {
    sample.stop(registry.timer("bookrush.analytics.book_scores.endpoint"));
  }

  public void jobItemFailed(String code) {
    analysisFailures.increment();
  }

  public <T> T time(java.util.function.Supplier<T> operation) {
    return analyzerDuration.record(operation);
  }

  public <T> T timePhase(String phase, java.util.function.Supplier<T> operation) {
    return Timer.builder("bookrush.analytics.phase.duration")
        .description("Duration of a persisted analytics processing phase")
        .tag("phase", phase)
        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
        .register(registry)
        .record(operation);
  }

  public <T> T timePhaseChecked(String phase, java.util.concurrent.Callable<T> operation)
      throws Exception {
    return Timer.builder("bookrush.analytics.phase.duration")
        .description("Duration of a persisted analytics processing phase")
        .tag("phase", phase)
        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
        .register(registry)
        .recordCallable(operation);
  }

  public void phaseCompleted(String phase) {
    registry.counter("bookrush.analytics.phase.completions", "phase", phase).increment();
  }

  public void phaseFailed(String phase) {
    registry.counter("bookrush.analytics.phase.failures", "phase", phase).increment();
  }

  public Duration durationEstimate() {
    return analyzerDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS) == 0
        ? Duration.ZERO
        : Duration.ofMillis(
            (long) analyzerDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
  }
}
