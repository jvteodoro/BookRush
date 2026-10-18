package com.bookrush.analytics.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic planner for resuming embedding batches without redoing durable work. */
final class EmbeddingResumePlanner {
  private EmbeddingResumePlanner() {}

  static List<Integer> pendingIndexes(List<UUID> excerptIds, Map<UUID, List<Double>> persisted) {
    var pending = new ArrayList<Integer>();
    for (int index = 0; index < excerptIds.size(); index++) {
      if (!persisted.containsKey(excerptIds.get(index))) pending.add(index);
    }
    return List.copyOf(pending);
  }
}
