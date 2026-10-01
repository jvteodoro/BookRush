package com.bookrush.analytics.features;

import java.util.Map;

/**
 * Immutable, serializable corpus model input. The caller supplies only eligible normalized text.
 */
public record CorpusFrequencyArtifact(
    String language,
    String corpusSnapshot,
    String tokenizerVersion,
    double smoothingAlpha,
    long tokenCount,
    int vocabularySize,
    Map<String, Long> counts,
    String sha256) {}
