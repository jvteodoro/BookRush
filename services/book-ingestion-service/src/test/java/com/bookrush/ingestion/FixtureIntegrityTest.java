package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class FixtureIntegrityTest {
  @Test
  void fixturesAreOfflineAndContainExpectedSourceRecords() throws Exception {
    var rdf = resource("/fixtures/gutenberg/catalog.rdf");
    var work = resource("/fixtures/openlibrary/works.tsv");
    var unicode = resource("/fixtures/books/unicode.txt");
    assertTrue(rdf.contains("ebooks/1342"));
    assertTrue(rdf.contains("https://example.test/"));
    assertTrue(work.contains("/works/OL1W"));
    assertTrue(unicode.contains("😀"));
    assertEquals(64, sha256(unicode).length());
  }

  private static String resource(String name) throws Exception {
    try (InputStream stream = FixtureIntegrityTest.class.getResourceAsStream(name)) {
      assertNotNull(stream, name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String sha256(String value) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    return java.util.HexFormat.of().formatHex(digest);
  }
}
