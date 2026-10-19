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

  public void count(String name, String outcome, double amount) {
    registry.counter(name, "outcome", outcome).increment(amount);
  }

  public void observeFeed(int candidates, int scored, int selected, int items, int excerpts) {
    count("bookrush.feed.candidates", "retrieved", candidates);
    count("bookrush.feed.candidates", "scored", scored);
    count("bookrush.feed.candidates", "selected", selected);
    count("bookrush.feed.items", "returned", items);
    count("bookrush.feed.excerpts", "attached", excerpts);
    count("bookrush.feed.requests", items == 0 ? "empty" : "with_items", 1);
  }
}
