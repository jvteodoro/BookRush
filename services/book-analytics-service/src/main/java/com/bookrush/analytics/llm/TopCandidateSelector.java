package com.bookrush.analytics.llm;

import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import java.util.*;

/** Limits expensive teacher/editorial work to deterministic top-K candidates. */
public final class TopCandidateSelector {
  private TopCandidateSelector() {}
  public static List<ExcerptCandidateGenerator.Candidate> topK(List<ExcerptCandidateGenerator.Candidate> candidates, Map<UUID,Double> scores, int k) {
    if(k<0) throw new IllegalArgumentException("k must be non-negative");
    return candidates.stream().sorted(Comparator.comparingDouble((ExcerptCandidateGenerator.Candidate c)->scores.getOrDefault(UUID.nameUUIDFromBytes(c.textSha256().getBytes()),0d)).reversed().thenComparing(ExcerptCandidateGenerator.Candidate::textSha256)).limit(k).toList();
  }
}
