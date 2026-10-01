package com.bookrush.analytics.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NarrativeInferenceProtocolTest {
  @Test
  void boundsNliAndPreservesRawScores() {
    var ids = IntStream.range(0, 60).mapToObj(Integer::toString).toList();
    var selected = NarrativeInferenceProtocol.select(ids, 50);
    assertEquals(50, selected.excerptIds().size());
    assertTrue(selected.truncated());
    var o =
        NarrativeInferenceProtocol.observation("conflict", "pt", new NliScore(.7, .2, .3), "rev");
    assertEquals(.7, o.entailment());
    assertEquals(.7 / 1.0, o.support());
    assertEquals(.8, o.confidence());
  }
}
