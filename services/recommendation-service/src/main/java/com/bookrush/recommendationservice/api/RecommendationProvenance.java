package com.bookrush.recommendationservice.api;

import com.bookrush.recommendationservice.ranking.AnalyticsScoreClient.BookScore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Deterministic, non-sensitive provenance for one recommendation candidate set. */
final class RecommendationProvenance {
  private RecommendationProvenance() {}

  static String candidateSetHash(List<?> books, Map<String, BookScore> scores,
      String modelVersion) {
    var canonical = books.stream()
        .map(RecommendationProvenance::bookId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .sorted(Comparator.naturalOrder())
        .map(id -> {
          BookScore score = scores.get(id);
          String value = score == null
              ? "null"
              : Double.toString(score.score()) + ":" + String.valueOf(score.rankerVersion());
          return id + "=" + value;
        })
        .collect(java.util.stream.Collectors.joining("|"));
    return sha256(modelVersion + "\n" + canonical);
  }

  static String bookId(Object book) {
    if (!(book instanceof Map<?, ?> map) || map.get("id") == null) return null;
    return String.valueOf(map.get("id"));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required for recommendation provenance", exception);
    }
  }
}
