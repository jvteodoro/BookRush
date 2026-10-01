package com.bookrush.analytics.features;

import java.util.*;

/** Context-relative similarity observations for an excerpt. */
public final class ContextSimilarityCalculator {
  private ContextSimilarityCalculator() {}

  public static Result calculate(float[] excerpt, float[] document, float[] chapter) {
    var values = new LinkedHashMap<String, Double>();
    if (excerpt == null || excerpt.length == 0) return new Result(Map.of(), "MODEL_UNAVAILABLE");
    if (document != null)
      values.put("semantic.context.document_cosine", EmbeddingMath.cosine(excerpt, document));
    if (chapter != null)
      values.put("semantic.context.chapter_cosine", EmbeddingMath.cosine(excerpt, chapter));
    return new Result(Map.copyOf(values), values.isEmpty() ? "MODEL_UNAVAILABLE" : "VALID");
  }

  public record Result(Map<String, Double> values, String status) {}
}
