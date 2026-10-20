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
    List<Map<String, Object>> books,
    Map<String, List<String>> sourcesByBook) {
  public CandidateSet {
    version = Objects.requireNonNullElse(version, "unknown");
    configurationHash = Objects.requireNonNullElse(configurationHash, "unknown");
    source = Objects.requireNonNullElse(source, "unknown");
    books = books == null ? List.of() : List.copyOf(books);
    sourcesByBook = sourcesByBook == null ? Map.of() : Map.copyOf(sourcesByBook);
  }

  public CandidateSet(String version, String configurationHash, String source,
      List<Map<String, Object>> books) {
    this(version, configurationHash, source, books,
        books == null ? Map.of() : books.stream().filter(book -> book.get("id") != null)
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                book -> String.valueOf(book.get("id")), book -> List.of(source),
                (left, right) -> left)));
  }

  /** IDs in a stable order, suitable for request provenance and replay. */
  public List<String> canonicalBookIds() {
    return books.stream().map(book -> book.get("id")).filter(Objects::nonNull)
        .map(String::valueOf).distinct().sorted(Comparator.naturalOrder()).toList();
  }

  public List<String> sourcesFor(String bookId) {
    return sourcesByBook.getOrDefault(bookId, List.of());
  }
}
