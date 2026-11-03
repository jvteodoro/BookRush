package com.bookrush.recommendationservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedEligibilityTest {
  @Test
  void rejectsMissingBlankAndMalformedAnalyticsExcerpts() {
    var missing = new LinkedHashMap<String, Object>();
    var blank = new LinkedHashMap<String, Object>();
    blank.put("excerpt", Map.of("id", UUID.randomUUID(), "text", "  "));
    var valid = new LinkedHashMap<String, Object>();
    valid.put("excerpt", Map.of("id", UUID.randomUUID(), "text", "A real excerpt."));

    assertFalse(FeedEligibility.hasAnalyticsExcerpt(missing));
    assertFalse(FeedEligibility.hasAnalyticsExcerpt(blank));
    assertTrue(FeedEligibility.hasAnalyticsExcerpt(valid));
  }

  @Test
  void retainsOnlyItemsWithPersistedText() {
    var first = new LinkedHashMap<String, Object>();
    first.put("excerpt", Map.of("id", UUID.randomUUID(), "text", "First"));
    var second = new LinkedHashMap<String, Object>();

    assertEquals(List.of(first), FeedEligibility.retainAnalyticsReady(List.of(first, second)));
  }
}
