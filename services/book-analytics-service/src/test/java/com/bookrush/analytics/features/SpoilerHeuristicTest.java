package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpoilerHeuristicTest {
  @Test
  void exposesOnlyVersionedExplainableComponents() {
    var values = SpoilerHeuristic.components(1.2, -0.2, 0.7);
    assertEquals("spoiler-components-v1", SpoilerHeuristic.VERSION);
    assertEquals(1d, values.get("position"));
    assertEquals(0d, values.get("resolution"));
    assertEquals(0.7d, values.get("revelation"));
    assertFalse(values.containsKey("spoiler_probability"));
  }
}
