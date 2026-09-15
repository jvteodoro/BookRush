package com.bookrush.analytics.jobs;

import java.util.*;

/** Ordered, resumable V1 stages. A failed late stage can reuse prior outputs by identity. */
public enum AnalyticsStage {
  VALIDATE_LANGUAGE, STRUCTURAL, LINGUISTIC, CORPUS_LEXICAL, STYLE,
  DOCUMENT_EMBEDDING, EXCERPT_GENERATION, EXCERPT_CHEAP_FEATURES,
  EXCERPT_EMBEDDINGS, PROTOTYPE_SCORES, NARRATIVE_NLI, EMOTION_NLI,
  HEURISTIC_RANK, DONE;
  public Optional<AnalyticsStage> next() { int i=ordinal()+1; return i<values().length?Optional.of(values()[i]):Optional.empty(); }
  public boolean precedes(AnalyticsStage other) { return ordinal()<other.ordinal(); }
}
