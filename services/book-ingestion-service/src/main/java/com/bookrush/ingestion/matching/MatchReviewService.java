package com.bookrush.ingestion.matching;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** In-memory review boundary; persistence is delegated to the catalog command API in deployment. */
@Service
public class MatchReviewService {
  private final Map<UUID, Review> reviews = new ConcurrentHashMap<>();
  public UUID create(MatchingEngine.Decision decision) {
    if (!"REVIEW_REQUIRED".equals(decision.status())) throw new IllegalArgumentException("decision is not reviewable");
    UUID id = UUID.randomUUID();
    reviews.put(id, new Review(id, 0, decision, "PENDING"));
    return id;
  }
  public Review decide(UUID id, long expectedVersion, String status) {
    if (!SetOfStatuses.contains(status)) throw new IllegalArgumentException("invalid review status");
    return reviews.compute(id, (key, current) -> {
      if (current == null) throw new IllegalArgumentException("review not found");
      if (current.version() != expectedVersion) throw new OptimisticConflictException(id);
      return new Review(id, expectedVersion + 1, current.match(), status);
    });
  }
  public record Review(UUID id, long version, MatchingEngine.Decision match, String status) {}
  public static final class OptimisticConflictException extends RuntimeException { public OptimisticConflictException(UUID id) { super("review changed: " + id); } }
  private static final java.util.Set<String> SetOfStatuses = java.util.Set.of("APPROVED", "REJECTED", "PENDING");
}
