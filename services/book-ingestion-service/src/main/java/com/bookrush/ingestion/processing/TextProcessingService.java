package com.bookrush.ingestion.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Produces deterministic processing artifacts without mutating the SOURCE asset. */
@Service
public final class TextProcessingService {
  private final PlainTextNormalizer normalizer = new PlainTextNormalizer();
  private final ChapterStructureWriter chapters;
  public TextProcessingService(ObjectMapper mapper) { this.chapters = new ChapterStructureWriter(mapper); }

  public ProcessedText process(UUID editionId, UUID sourceVersionId, String input, List<ChapterStructureWriter.Chapter> structure) throws Exception {
    String normalized = normalizer.removeKnownGutenbergDelimiters(normalizer.normalize(input));
    String hash = sha256(normalized);
    byte[] chapterJson = chapters.write(editionId, sourceVersionId, normalized, hash, structure);
    return new ProcessedText(normalized.getBytes(StandardCharsets.UTF_8), chapterJson, hash);
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
  public record ProcessedText(byte[] normalizedTxt, byte[] chaptersJson, String sha256) {}
}
