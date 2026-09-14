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
  private final Timer analyzerDuration;

  public AnalyticsMetrics(MeterRegistry registry) {
    jobsCreated = registry.counter("bookrush.analytics.jobs", "outcome", "created");
    jobsReplayed = registry.counter("bookrush.analytics.jobs", "outcome", "replayed");
    jobsConflicted = registry.counter("bookrush.analytics.jobs", "outcome", "conflict");
    excerptsListed = registry.counter("bookrush.analytics.excerpts", "operation", "listed");
    analysisFailures = registry.counter("bookrush.analytics.failures", "stage", "analysis");
    generatedExcerpts = registry.counter("bookrush.analytics.excerpts", "operation", "generated");
    analyzerDuration = registry.timer("bookrush.analytics.analyzer.duration");
  }
  public void jobCreated() { jobsCreated.increment(); }
  public void jobReplayed() { jobsReplayed.increment(); }
  public void jobConflict() { jobsConflicted.increment(); }
  public void excerptsListed(int count) { excerptsListed.increment(Math.max(0, count)); }
  public void analysisFailure() { analysisFailures.increment(); }
  public void excerptsGenerated(int count) { generatedExcerpts.increment(Math.max(0, count)); }
  public void jobItemFailed(String code) { analysisFailures.increment(); }
  public <T> T time(java.util.function.Supplier<T> operation) { return analyzerDuration.record(operation); }
  public Duration durationEstimate() { return analyzerDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS) == 0 ? Duration.ZERO : Duration.ofMillis((long) analyzerDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS)); }
}
