package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MMRDiversitySelectorTest {
  @Test
  void selectsRelevantButDiverseAndDeterministicallyBreaksTies() {
    var a = new MMRDiversitySelector.Candidate("a", .9, new float[] {1, 0});
    var b = new MMRDiversitySelector.Candidate("b", .8, new float[] {.99f, .01f});
    var c = new MMRDiversitySelector.Candidate("c", .7, new float[] {0, 1});
    var selected = MMRDiversitySelector.select(List.of(a, b, c), 2, 1, 1);
    assertEquals(
        List.of("a", "c"), selected.stream().map(MMRDiversitySelector.Candidate::id).toList());
    assertTrue(selected.get(1).selectionScore() < selected.get(0).selectionScore());
  }
}
