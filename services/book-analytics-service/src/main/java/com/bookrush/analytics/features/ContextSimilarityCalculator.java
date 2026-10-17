package com.bookrush.analytics.features;

import java.util.*;

/** Context-relative similarity observations for an excerpt. */
public final class ContextSimilarityCalculator {
  private ContextSimilarityCalculator() {}

  public static Result calculate(float[] excerpt, float[] document, float[] chapter) {
    return calculate(excerpt, null, null, chapter, document);
  }

  public static Result calculate(
      float[] excerpt, float[] previous, float[] next, float[] chapter, float[] document) {
    var values = new LinkedHashMap<String, Double>();
    if (excerpt == null || excerpt.length == 0) return new Result(Map.of(), "MODEL_UNAVAILABLE");
    if (previous != null) {
      double similarity = EmbeddingMath.cosine(excerpt, previous);
      values.put("semantic.context.previous_cosine", similarity);
      values.put("semantic.shift.in", 1d - similarity);
    }
    if (next != null) {
      double similarity = EmbeddingMath.cosine(excerpt, next);
      values.put("semantic.context.next_cosine", similarity);
      values.put("semantic.shift.out", 1d - similarity);
    }
    if (document != null)
      values.put("semantic.context.document_cosine", EmbeddingMath.cosine(excerpt, document));
    if (chapter != null)
      values.put("semantic.context.chapter_cosine", EmbeddingMath.cosine(excerpt, chapter));
    if (values.containsKey("semantic.shift.in") || values.containsKey("semantic.shift.out")) {
      values.put(
          "semantic.shift.max",
          Math.max(
              values.getOrDefault("semantic.shift.in", 0d),
              values.getOrDefault("semantic.shift.out", 0d)));
    }
    return new Result(Map.copyOf(values), values.isEmpty() ? "MODEL_UNAVAILABLE" : "VALID");
  }

  public record Result(Map<String, Double> values, String status) {}
}
