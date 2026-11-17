package com.bookrush.recommendationservice.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class FeedMetricsTest {
  @Test
  void recordsStageTimerWhenOperationFails() {
    var registry = new SimpleMeterRegistry();
    var metrics = new FeedMetrics(registry);

    assertThrows(IllegalStateException.class,
        () -> metrics.time("analytics_scores", () -> { throw new IllegalStateException("boom"); }));

    assertEquals(1, registry.timer("bookrush.feed.stage.duration", "stage", "analytics_scores").count());
  }

  @Test
  void recordsExactlyOneTotalTimerAndOutcome() {
    var registry = new SimpleMeterRegistry();
    var metrics = new FeedMetrics(registry);
    var sample = metrics.start();
    metrics.recordResult("error");
    metrics.stopTotal(sample);

    assertEquals(1, registry.timer("bookrush.feed.stage.duration", "stage", "total").count());
    assertEquals(1, registry.counter("bookrush.feed.requests", "outcome", "error").count());
  }

  @Test
  void recordsLowCardinalityPersonalizedPathsAndFallbackReasons() {
    var registry = new SimpleMeterRegistry();
    var metrics = new FeedMetrics(registry);

    metrics.observePersonalization("FALLBACK", "disabled", 0, 0, 0, 0);
    metrics.observePersonalization("SHADOW", "shadow", 0.4, 2, 1, 3);
    metrics.observePersonalization("PERSONALIZED", "ready", 0.8, 4, 0, 0);

    assertEquals(1, registry.counter("bookrush.feed.personalized.path",
        "outcome", "fallback").count());
    assertEquals(1, registry.counter("bookrush.feed.personalized.path",
        "outcome", "shadow").count());
    assertEquals(1, registry.counter("bookrush.feed.personalized.path",
        "outcome", "personalized").count());
    assertEquals(1, registry.counter("bookrush.feed.personalized.reason",
        "outcome", "disabled").count());
    assertEquals(1, registry.counter("bookrush.feed.personalized.reason",
        "outcome", "shadow").count());
    assertEquals(1, registry.counter("bookrush.feed.personalized.reason",
        "outcome", "ready").count());
  }
}
