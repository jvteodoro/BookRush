package com.bookrush.ingestion.source;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

/** Chooses a bounded, deterministic representation without downloading it. */
public final class AssetRepresentationSelector {
  public List<Representation> select(List<Representation> candidates, int limit) {
    if (limit < 1) throw new IllegalArgumentException("limit must be positive");
    return candidates.stream().filter(r -> r.uri() != null && r.contentType() != null)
        .sorted(Comparator.comparingInt((Representation r) -> rank(r.contentType())).reversed().thenComparing(r -> r.uri().toString()))
        .limit(limit).toList();
  }
  private int rank(String type) { return switch (type.toLowerCase()) { case "application/epub+zip" -> 4; case "text/plain" -> 3; case "text/html" -> 2; case "application/pdf" -> 1; default -> 0; }; }
  public record Representation(URI uri, String contentType, long sizeBytes, String label) {}
}
