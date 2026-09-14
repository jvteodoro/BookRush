package com.bookrush.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Extracts raw Open Library subject terms without treating them as canonical taxonomy. */
public final class OpenLibrarySubjectExtractor {
  private OpenLibrarySubjectExtractor() {}

  public static List<String> extract(JsonNode payload) {
    var subjects = payload.path("subjects");
    if (!subjects.isArray()) return List.of();
    var values = new java.util.ArrayList<String>();
    subjects.forEach(node -> {
      var value = node.isTextual() ? node.asText() : node.path("name").asText(null);
      if (value != null && !value.isBlank() && !values.contains(value.trim())) values.add(value.trim());
    });
    return List.copyOf(values);
  }
}
