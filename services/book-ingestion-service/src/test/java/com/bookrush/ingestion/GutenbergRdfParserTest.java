package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.source.GutenbergRdfParser;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GutenbergRdfParserTest {
  @Test void parsesFixtureWithoutResolvingExternalEntities() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/fixtures/gutenberg/catalog.rdf")) {
      var records = new GutenbergRdfParser().parse(input, 10);
      assertEquals(1, records.size());
      assertEquals("ebooks/1342", records.get(0).externalId());
      assertEquals("Pride and Prejudice", records.get(0).title());
      assertEquals("https://example.test/cache/epub/1342/pg1342.epub", records.get(0).epubUrl());
    }
  }
}
