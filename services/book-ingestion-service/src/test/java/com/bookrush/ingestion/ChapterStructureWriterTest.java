package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.processing.ChapterStructureWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChapterStructureWriterTest {
  @Test void fallbackUsesCodePointLengthForEmoji() throws Exception {
    var text = "A😀B";
    var json = new ChapterStructureWriter(new ObjectMapper()).write(UUID.randomUUID(), UUID.randomUUID(), text, "a".repeat(64), null);
    var root = new ObjectMapper().readTree(json);
    assertEquals("UNICODE_CODE_POINT", root.get("offsetUnit").asText());
    assertEquals(3, root.get("chapters").get(0).get("end").asInt());
  }

  @Test void rejectsOutOfBoundsIntervals() {
    assertThrows(Exception.class, () -> new ChapterStructureWriter(new ObjectMapper()).write(UUID.randomUUID(), UUID.randomUUID(), "abc", "a".repeat(64),
        java.util.List.of(new ChapterStructureWriter.Chapter("x", null, 0, "x", 0, 4, null, "EXPLICIT"))));
  }
}
