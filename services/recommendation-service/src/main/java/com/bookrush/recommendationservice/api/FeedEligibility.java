package com.bookrush.recommendationservice.api;

import java.util.List;
import java.util.Map;

/** Product eligibility rules for the analytics-backed reader feed. */
final class FeedEligibility {
  private FeedEligibility() {}

  static boolean hasAnalyticsExcerpt(Map<String, Object> item) {
    Object value = item.get("excerpt");
    if (!(value instanceof Map<?, ?> excerpt)) return false;
    Object id = excerpt.get("id");
    Object text = excerpt.get("text");
    return id != null && text instanceof String excerptText && !excerptText.isBlank();
  }

  static List<Map<String, Object>> retainAnalyticsReady(List<Map<String, Object>> items) {
    return items.stream().filter(FeedEligibility::hasAnalyticsExcerpt).toList();
  }
}
