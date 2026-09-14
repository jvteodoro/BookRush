package com.bookrush.analytics.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class TextAssetReaderTest {
  @Test void verifiesUnicodeCodePointsAndHash() throws Exception {
    var text = "A😀\nPergunta?\n";
    var bytes = text.getBytes(StandardCharsets.UTF_8);
    var hash = HexFormat.of().formatHex(TextAssetReader.sha256(bytes));
    assertEquals(text, TextAssetReader.readNormalized(new ByteArrayInputStream(bytes), bytes.length, hash));
    assertEquals(13, TextAssetReader.codePointLength(text));
  }
  @Test void rejectsHashDrift() {
    var bytes = "texto\n".getBytes(StandardCharsets.UTF_8);
    assertThrows(TextAssetReader.IntegrityException.class, () -> TextAssetReader.readNormalized(new ByteArrayInputStream(bytes), bytes.length, "0".repeat(64)));
  }
  @Test void rejectsCrLf() throws Exception {
    var bytes = "texto\r\n".getBytes(StandardCharsets.UTF_8);
    var hash = HexFormat.of().formatHex(TextAssetReader.sha256(bytes));
    var error = assertThrows(TextAssetReader.IntegrityException.class, () -> TextAssetReader.readNormalized(new ByteArrayInputStream(bytes), bytes.length, hash));
    assertEquals("TEXT_NOT_LF", error.getMessage());
  }
}
