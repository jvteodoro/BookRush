package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.processing.*;
import org.junit.jupiter.api.Test;

class TextProcessorTest {
  @Test void normalizationIsStableAndUsesNfcLf() {
    var normalizer = new PlainTextNormalizer();
    var result = normalizer.normalize("\uFEFFCafe\u0301\r\n\r\n😀");
    assertEquals("Café\n\n😀\n", result);
    assertEquals(result, normalizer.normalize(result));
  }

  @Test void htmlExtractorDropsScriptsAndPreservesBlocks() {
    var result = new HtmlTextExtractor().extract("<h1>Title</h1><p>Hello <em>world</em>.</p><script>x</script>");
    assertEquals("Title\nHello world.\n", result);
  }

  @Test void onlyKnownGutenbergMarkersAreRemoved() {
    var normalizer = new PlainTextNormalizer();
    assertTrue(normalizer.removeKnownGutenbergDelimiters("header\n*** START OF THE PROJECT GUTENBERG EBOOK X ***\nbody\n*** END OF THE PROJECT GUTENBERG EBOOK X ***").contains("body"));
    assertEquals("license text", normalizer.removeKnownGutenbergDelimiters("license text"));
  }
}
