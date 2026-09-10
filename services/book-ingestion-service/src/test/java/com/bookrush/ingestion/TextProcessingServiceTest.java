package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;
import com.bookrush.ingestion.processing.TextProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextProcessingServiceTest {
  @Test void producesStableTextAndChapterArtifacts() throws Exception {
    var service = new TextProcessingService(new ObjectMapper());
    var result = service.process(UUID.randomUUID(), UUID.randomUUID(), "*** START OF THE PROJECT GUTENBERG EBOOK X ***\r\nHello\r\n\r\nworld\r\n*** END OF THE PROJECT GUTENBERG EBOOK X ***", null);
    assertEquals("Hello\n\nworld\n", new String(result.normalizedTxt(), java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(new String(result.chaptersJson(), java.nio.charset.StandardCharsets.UTF_8).contains("UNICODE_CODE_POINT"));
    assertEquals(64, result.sha256().length());
  }
}
