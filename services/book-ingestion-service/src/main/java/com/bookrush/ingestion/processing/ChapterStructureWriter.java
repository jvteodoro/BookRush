package com.bookrush.ingestion.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;

/** Serializes chapter intervals after normalization, using Unicode code points and half-open bounds. */
public final class ChapterStructureWriter {
  private final ObjectMapper mapper;

  public ChapterStructureWriter(ObjectMapper mapper) { this.mapper = mapper; }

  public byte[] write(UUID editionId, UUID textVersionId, String text, String textSha256, List<Chapter> chapters) throws Exception {
    var actual = chapters == null || chapters.isEmpty()
        ? List.of(new Chapter("document", null, 0, "DOCUMENT", 0, text.codePointCount(0, text.length()), null, "FALLBACK"))
        : chapters;
    int length = text.codePointCount(0, text.length());
    for (var chapter : actual) {
      if (chapter.start() < 0 || chapter.end() < chapter.start() || chapter.end() > length) {
        throw new IllegalArgumentException("chapter interval outside normalized text");
      }
    }
    var document = new Document("1.0", editionId, textVersionId, textSha256, "UTF-8", "NFC_LF",
        "UNICODE_CODE_POINT", "HALF_OPEN", actual);
    return mapper.writeValueAsBytes(document);
  }

  public record Chapter(String key, String parent, int position, String title, int start, int end,
      String sourceHref, String confidence) {}
  public record Document(String schema, UUID editionId, UUID textAssetVersionId, String textSha256,
      String encoding, String normalization, String offsetUnit, String interval, List<Chapter> chapters) {}
}
