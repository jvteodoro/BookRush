package com.bookrush.recommendationservice.excerpt;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic selection; it never invents a score when analytics has none. */
public final class ExcerptSelection {
  private ExcerptSelection() {}

  public static FeedExcerpt select(List<Map<String, Object>> rows, ExcerptPolicyProperties policy) {
    return rows.stream()
        .map(row -> toExcerpt(row, policy))
        .filter(java.util.Objects::nonNull)
        .filter(e -> policy.rankerVersion().isBlank() || e.rank() == null
            || policy.rankerVersion().equals(e.rank().version()))
        .sorted(
            Comparator.<FeedExcerpt, Boolean>comparing(e -> e.rank() != null).reversed()
                .thenComparing(
                    e -> e.rank() == null || e.rank().score() == null
                        ? Double.NEGATIVE_INFINITY : e.rank().score(),
                    Comparator.reverseOrder())
                .thenComparing(FeedExcerpt::generatorVersion, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(FeedExcerpt::startCodepoint)
                .thenComparing(FeedExcerpt::id))
        .findFirst()
        .orElse(null);
  }

  private static FeedExcerpt toExcerpt(Map<String, Object> row, ExcerptPolicyProperties policy) {
    try {
      Object textValue = row.get("text");
      String text = textValue == null ? "" : String.valueOf(textValue).trim();
      if (text.isBlank() || text.codePointCount(0, text.length()) > policy.maxTextCodepoints()) return null;
      if (policy.requireBodyEligible() && Boolean.FALSE.equals(row.get("body_eligible"))) return null;
      String exclusion = row.get("exclusion_reason") == null ? null : String.valueOf(row.get("exclusion_reason"));
      if (exclusion != null && !exclusion.isBlank()) return null;
      UUID id = UUID.fromString(String.valueOf(row.get("id")));
      UUID version = UUID.fromString(String.valueOf(row.get("source_asset_version_id")));
      int start = number(row.get("start_codepoint"));
      int end = number(row.get("end_codepoint"));
      if (start < 0 || end <= start) return null;
      String hash = String.valueOf(row.getOrDefault("text_sha256", ""));
      if (!hash.matches("[0-9a-f]{64}")) return null;
      Double score = row.get("candidate_score") instanceof Number n ? n.doubleValue() : null;
      String ranker = row.get("ranker_version") == null ? null : String.valueOf(row.get("ranker_version"));
      var rank = ranker == null ? null : new FeedExcerpt.Rank(ranker, score);
      return new FeedExcerpt(id, text, version, hash, start, end,
          String.valueOf(row.getOrDefault("generation_method", "unknown")),
          String.valueOf(row.getOrDefault("generator_version", "unknown")), rank);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private static int number(Object value) {
    return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
  }
}
