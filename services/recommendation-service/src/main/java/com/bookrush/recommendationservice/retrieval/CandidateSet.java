package com.bookrush.recommendationservice.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned, bounded candidate snapshot passed from retrieval to ranking. */
public record CandidateSet(
    String version,
    String configurationHash,
    String source,
    List<Map<String, Object>> books) {
  public CandidateSet {
    version = Objects.requireNonNullElse(version, "unknown");
    configurationHash = Objects.requireNonNullElse(configurationHash, "unknown");
    source = Objects.requireNonNullElse(source, "unknown");
    books = books == null ? List.of() : List.copyOf(books);
  }

  /** IDs in a stable order, suitable for request provenance and replay. */
  public List<String> canonicalBookIds() {
    return books.stream().map(book -> book.get("id")).filter(Objects::nonNull)
        .map(String::valueOf).distinct().sorted(Comparator.naturalOrder()).toList();
  }
}
