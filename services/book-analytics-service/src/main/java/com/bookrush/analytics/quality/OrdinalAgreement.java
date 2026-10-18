package com.bookrush.analytics.quality;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic ordinal quality policy used by the annotation queue.
 *
 * <p>This is intentionally named ordinal agreement rather than Krippendorff's alpha: it is
 * the current quality-v1 pairwise agreement approximation and is kept separate so it can be
 * replaced by a formal alpha implementation without changing the HTTP layer.
 */
public final class OrdinalAgreement {
  private static final double MAX_DISTANCE = 4.0;

  private OrdinalAgreement() {}

  public record Rating(String itemId, String dimension, int value) {
    public Rating {
      Objects.requireNonNull(itemId, "itemId");
      Objects.requireNonNull(dimension, "dimension");
      if (value < 1 || value > 5) throw new IllegalArgumentException("ordinal value must be 1..5");
    }
  }

  /** Returns the mean squared normalized pairwise agreement for each item/dimension group. */
  public static double agreement(List<Rating> ratings) {
    double disagreement = 0.0;
    long pairs = 0;
    for (int left = 0; left < ratings.size(); left++) {
      for (int right = left + 1; right < ratings.size(); right++) {
        Rating a = ratings.get(left);
        Rating b = ratings.get(right);
        if (!a.itemId().equals(b.itemId()) || !a.dimension().equals(b.dimension())) continue;
        double distance = Math.abs(a.value() - b.value()) / MAX_DISTANCE;
        disagreement += distance * distance;
        pairs++;
      }
    }
    return pairs == 0 ? 0.0 : Math.max(-1.0, 1.0 - disagreement / pairs);
  }

  /**
   * Computes Krippendorff's alpha for ordinal ratings using squared normalized distance.
   *
   * <p>Ratings are grouped by item and dimension. A value of {@code 0.0} means either insufficient
   * data or agreement no better than chance; a value of {@code 1.0} means perfect agreement on
   * comparable ratings.
   */
  public static double krippendorffAlphaOrdinal(List<Rating> ratings) {
    Map<String, List<Integer>> units = new HashMap<>();
    Map<Integer, Integer> valueCounts = new HashMap<>();
    int totalRatings = 0;
    for (Rating rating : ratings) {
      units.computeIfAbsent(rating.itemId() + "\u0000" + rating.dimension(), ignored -> new java.util.ArrayList<>())
          .add(rating.value());
      valueCounts.merge(rating.value(), 1, Integer::sum);
      totalRatings++;
    }

    double observedDistance = 0.0;
    long observedPairs = 0;
    for (List<Integer> unit : units.values()) {
      for (int left = 0; left < unit.size(); left++) {
        for (int right = left + 1; right < unit.size(); right++) {
          observedDistance += squaredDistance(unit.get(left), unit.get(right));
          observedPairs++;
        }
      }
    }
    if (observedPairs == 0 || totalRatings < 2) return 0.0;

    double expectedDistance = 0.0;
    long expectedPairs = 0;
    for (Map.Entry<Integer, Integer> left : valueCounts.entrySet()) {
      for (Map.Entry<Integer, Integer> right : valueCounts.entrySet()) {
        int available = left.getValue() * right.getValue();
        if (left.getKey().equals(right.getKey())) available -= left.getValue();
        if (available <= 0) continue;
        expectedDistance += available * squaredDistance(left.getKey(), right.getKey());
        expectedPairs += available;
      }
    }
    if (expectedPairs == 0 || expectedDistance == 0.0) return 1.0;
    double alpha = 1.0 - (observedDistance / observedPairs) / (expectedDistance / expectedPairs);
    return Math.max(-1.0, Math.min(1.0, alpha));
  }

  private static double squaredDistance(int left, int right) {
    double distance = Math.abs(left - right) / MAX_DISTANCE;
    return distance * distance;
  }

  /** Returns whether the range for one item/dimension is at or above the configured threshold. */
  public static boolean disagrees(List<Rating> ratings, int threshold) {
    if (threshold < 1 || threshold > 4) throw new IllegalArgumentException("threshold must be 1..4");
    if (ratings.isEmpty()) return false;
    int min = ratings.stream().map(Rating::value).min(Comparator.naturalOrder()).orElseThrow();
    int max = ratings.stream().map(Rating::value).max(Comparator.naturalOrder()).orElseThrow();
    return max - min >= threshold;
  }
}
