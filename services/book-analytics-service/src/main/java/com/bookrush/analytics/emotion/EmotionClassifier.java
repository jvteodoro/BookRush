package com.bookrush.analytics.emotion;

import java.util.Map;

/** SPI for local emotion models. Implementations must never download models on startup. */
public interface EmotionClassifier {
  Result classify(String text, String language);
  record Result(boolean supported, Map<String, Double> scores, String model, String version) {
    public static Result unsupported(String reason) { return new Result(false, Map.of(), reason, null); }
  }
}
