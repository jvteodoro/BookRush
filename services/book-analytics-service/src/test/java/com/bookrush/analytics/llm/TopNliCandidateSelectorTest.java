package com.bookrush.analytics.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import java.util.*;
import org.junit.jupiter.api.Test;

class TopNliCandidateSelectorTest {
  @Test
  void capsAtFiftyAndIsDeterministic() {
    var chapter = UUID.randomUUID();
    var candidates = new ArrayList<ExcerptCandidateGenerator.V2Candidate>();
    var ids = new ArrayList<UUID>();
    var vectors = new HashMap<UUID, List<Double>>();
    for (int i = 0; i < 60; i++) {
      candidates.add(new ExcerptCandidateGenerator.V2Candidate(
          chapter, i, i + 20, "A meaningful excerpt with distinct words " + i,
          8, 2, "hash-" + i, "v2"));
      var id = UUID.nameUUIDFromBytes(("id-" + i).getBytes());
      ids.add(id);
      vectors.put(id, List.of(1d, i % 2 == 0 ? 0d : 1d));
    }
    var first = TopNliCandidateSelector.select(candidates, ids, vectors, 100);
    var second = TopNliCandidateSelector.select(candidates, ids, vectors, 100);
    assertEquals(50, first.size());
    assertEquals(first, second);
  }

  @Test
  void selectsAcrossChaptersWhenScoresAreComparable() {
    var firstChapter = UUID.randomUUID();
    var secondChapter = UUID.randomUUID();
    var one = candidate(firstChapter, "one", 0);
    var two = candidate(firstChapter, "two", 1);
    var three = candidate(secondChapter, "three", 2);
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var vectors = Map.of(ids.get(0), List.of(1d, 0d), ids.get(1), List.of(1d, 0d), ids.get(2), List.of(0d, 1d));
    var selected = TopNliCandidateSelector.select(List.of(one, two, three), ids, vectors, 2);
    assertEquals(2, selected.size());
    assertTrue(selected.stream().anyMatch(s -> secondChapter.equals(s.candidate().chapterId())));
  }

  private static ExcerptCandidateGenerator.V2Candidate candidate(UUID chapter, String text, int offset) {
    return new ExcerptCandidateGenerator.V2Candidate(chapter, offset, offset + 20,
        "A meaningful excerpt with " + text + " words", 8, 2, "hash-" + text, "v2");
  }
}
