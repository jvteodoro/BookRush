package com.bookrush.analytics.emotion;

/** Safe default until a verified local model artifact is configured. */
public final class DisabledEmotionClassifier implements EmotionClassifier {
  @Override public Result classify(String text, String language) {
    return Result.unsupported("emotion-model-disabled");
  }
}
