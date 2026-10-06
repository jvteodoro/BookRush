package com.bookrush.recommendationservice.excerpt;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Enriches a bounded feed concurrently; every failure degrades to a plain book item. */
public final class ExcerptEnrichmentService implements AutoCloseable {
  private final AnalyticsExcerptClient client;
  private final ExcerptPolicyProperties policy;
  private final ExecutorService executor;
  private final MeterRegistry metrics;

  public ExcerptEnrichmentService(AnalyticsExcerptClient client, ExcerptPolicyProperties policy,
      MeterRegistry metrics) {
    this.client = client;
    this.policy = policy;
    this.metrics = metrics;
    this.executor = Executors.newFixedThreadPool(policy.maxConcurrency(), r -> {
      Thread thread = new Thread(r, "recommendation-excerpt");
      thread.setDaemon(true);
      return thread;
    });
  }

  public void enrich(List<Map<String, Object>> items) {
    if (!policy.enabled() || policy.percentage() == 0 || items.isEmpty()) return;
    long started = System.nanoTime();
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (Map<String, Object> item : items) {
      UUID bookId = id(item.get("book"));
      if (bookId == null || !inRollout(bookId)) continue;
      futures.add(CompletableFuture.runAsync(() -> {
        FeedExcerpt excerpt = client.find(bookId);
        if (excerpt != null) {
          item.put("excerpt", excerpt.asMap());
          metrics.counter("bookrush.feed.excerpt.attach", "outcome", "attached").increment();
        } else {
          metrics.counter("bookrush.feed.excerpt.fallback", "reason", "unavailable_or_missing").increment();
        }
      }, executor));
    }
    try {
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
          .orTimeout(policy.totalBudget().toMillis(), TimeUnit.MILLISECONDS).join();
    } catch (RuntimeException timeout) {
      futures.forEach(f -> f.cancel(true));
      metrics.counter("bookrush.feed.excerpt.fallback", "reason", "budget_exceeded").increment();
    } finally {
      metrics.timer("bookrush.feed.excerpt.enrichment").record(Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private boolean inRollout(UUID bookId) {
    int bucket = Math.floorMod(bookId.hashCode(), 100);
    return bucket < policy.percentage();
  }

  @SuppressWarnings("unchecked")
  private UUID id(Object book) {
    if (!(book instanceof Map<?, ?> map) || map.get("id") == null) return null;
    try { return UUID.fromString(String.valueOf(map.get("id"))); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  @Override public void close() { executor.shutdownNow(); }
}
