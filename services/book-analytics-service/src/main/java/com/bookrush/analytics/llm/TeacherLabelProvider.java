package com.bookrush.analytics.llm;

import java.util.Map;

/** Optional teacher interface. Implementations must enforce budget, timeout and schema validation. */
public interface TeacherLabelProvider {
  Result label(String text, Prompt prompt);
  record Prompt(String version, String template) {}
  record Result(boolean enabled, Map<String,Double> labels, String model, String modelVersion, long inputTokens, long outputTokens, String cacheKey) {
    public static Result disabled(String key) { return new Result(false, Map.of(), "disabled", null, 0, 0, key); }
  }
}
