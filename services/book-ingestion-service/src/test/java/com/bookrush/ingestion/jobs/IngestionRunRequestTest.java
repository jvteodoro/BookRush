package com.bookrush.ingestion.jobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionRunRequestTest {
  @Test
  void normalizesSourceIdsAndLanguagesForStableFingerprints() {
    var request = new IngestionRunRequest("gutenberg", List.of("1342", "1342", "84"), List.of("PT", "en"), null, false, true);
    assertEquals("GUTENBERG", request.source());
    assertEquals(List.of("1342", "84"), request.externalIds());
    assertEquals(List.of("en", "pt"), request.languages());
    assertEquals(100, request.maxItems());
  }

  @Test
  void rejectsArbitraryUrlsAsGutenbergIdentifiers() {
    assertThrows(IllegalArgumentException.class, () -> new IngestionRunRequest("GUTENBERG", List.of("https://example.invalid/book.epub"), List.of(), 1, false, true));
  }
}
