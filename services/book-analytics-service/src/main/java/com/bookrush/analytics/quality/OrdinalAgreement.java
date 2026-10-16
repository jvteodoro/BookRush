package com.bookrush.analytics.quality;

import java.util.Comparator;
import java.util.List;
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

  /** Returns whether the range for one item/dimension is at or above the configured threshold. */
  public static boolean disagrees(List<Rating> ratings, int threshold) {
    if (threshold < 1 || threshold > 4) throw new IllegalArgumentException("threshold must be 1..4");
    if (ratings.isEmpty()) return false;
    int min = ratings.stream().map(Rating::value).min(Comparator.naturalOrder()).orElseThrow();
    int max = ratings.stream().map(Rating::value).max(Comparator.naturalOrder()).orElseThrow();
    return max - min >= threshold;
  }
}
