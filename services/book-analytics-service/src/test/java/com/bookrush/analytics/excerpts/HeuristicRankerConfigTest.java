package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HeuristicRankerConfigTest {
  @Test
  void loadsVersionedUnvalidatedBootstrapConfiguration() {
    var config = HeuristicRankerConfig.load(
        getClass().getResourceAsStream("/analytics-ranker-v1.json"));
    assertEquals("EXCERPT_HEURISTIC_RANKER_V1", config.version());
    assertEquals(.20d, config.weights().get("question_ratio"));
  }

  @Test
  void rejectsPromotionStateInBootstrapArtifact() {
    var json = "{\"code\":\"EXCERPT_HEURISTIC_RANKER\",\"version\":\"v1\",\"state\":\"VALIDATED\",\"weights\":{\"x\":1}}";
    assertThrows(IllegalArgumentException.class, () -> HeuristicRankerConfig.load(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
  }
}
