package com.bookrush.recommendationservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Low-cardinality timers for one real feed request. IDs and subjects are never tags. */
@Component
public final class FeedMetrics {
  private final MeterRegistry registry;

  public FeedMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <T> T time(String stage, Supplier<T> operation) {
    long started = System.nanoTime();
    try {
      return operation.get();
    } finally {
      registry.timer("bookrush.feed.stage.duration", "stage", stage)
          .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
  }

  public Timer.Sample start() {
    return Timer.start(registry);
  }

  public void stopTotal(Timer.Sample sample) {
    sample.stop(registry.timer("bookrush.feed.stage.duration", "stage", "total"));
  }

  public void recordResult(String outcome) {
    registry.counter("bookrush.feed.requests", "outcome", outcome).increment();
  }

  public void count(String name, String outcome, double amount) {
    registry.counter(name, "outcome", outcome).increment(amount);
  }

  public void observeFeed(int candidates, int scored, int selected, int items, int excerpts) {
    count("bookrush.feed.candidates", "retrieved", candidates);
    count("bookrush.feed.candidates", "scored", scored);
    count("bookrush.feed.candidates", "selected", selected);
    count("bookrush.feed.items", "returned", items);
    count("bookrush.feed.excerpts", "attached", excerpts);
  }

  public void observePersonalization(String path, String reason, double confidence,
      int profileSignals, int shadowOverlap, int shadowCompared) {
    observePersonalization(path, reason, confidence, profileSignals, shadowOverlap, shadowCompared, 0);
  }

  public void observePersonalization(String path, String reason, double confidence,
      int profileSignals, int shadowOverlap, int shadowCompared, int rankMovement) {
    count("bookrush.feed.personalized.path", safe(path), 1);
    count("bookrush.feed.personalized.reason", safe(reason), 1);
    count("bookrush.feed.personalized.profile.signals", "count", profileSignals);
    count("bookrush.feed.personalized.shadow.compared", "count", shadowCompared);
    count("bookrush.feed.personalized.shadow.overlap", "count", shadowOverlap);
    count("bookrush.feed.personalized.shadow.rank_movement", "count", rankMovement);
    count("bookrush.feed.personalized.profile.confidence", confidenceBucket(confidence), 1);
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static String confidenceBucket(double value) {
    if (!Double.isFinite(value) || value < .25) return "0-25";
    if (value < .5) return "25-50";
    if (value < .75) return "50-75";
    return "75-100";
  }
}
