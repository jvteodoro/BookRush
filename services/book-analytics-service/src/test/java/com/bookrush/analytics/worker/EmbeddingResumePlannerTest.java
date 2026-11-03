package com.bookrush.analytics.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingResumePlannerTest {
  @Test
  void resumesOnlyMissingEmbeddingsAndPreservesOriginalOrder() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    var third = UUID.randomUUID();

    assertEquals(
        List.of(0, 2),
        EmbeddingResumePlanner.pendingIndexes(
            List.of(first, second, third), Map.of(second, List.of(0.1d))));
  }

  @Test
  void completedBatchHasNoPendingIndexes() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();

    assertEquals(
        List.of(),
        EmbeddingResumePlanner.pendingIndexes(
            List.of(first, second), Map.of(first, List.of(1d), second, List.of(2d))));
  }
}
