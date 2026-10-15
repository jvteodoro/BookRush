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

  public TextProcessingService(ObjectMapper mapper) {
    this.chapters = new ChapterStructureWriter(mapper);
  }

  public ProcessedText process(
      UUID editionId,
      UUID sourceVersionId,
      String input,
      List<ChapterStructureWriter.Chapter> structure)
      throws Exception {
    String normalizedInput = normalizer.normalize(input);
    String normalized = normalizer.removeKnownGutenbergDelimiters(normalizedInput);
    String hash = sha256(normalized);
    byte[] chapterJson =
        chapters.write(
            editionId,
            sourceVersionId,
            normalized,
            hash,
            remapStructure(structure, normalizedInput, normalized));
    return new ProcessedText(normalized.getBytes(StandardCharsets.UTF_8), chapterJson, hash);
  }

  private List<ChapterStructureWriter.Chapter> remapStructure(
      List<ChapterStructureWriter.Chapter> structure, String source, String normalized) {
    if (structure == null || structure.isEmpty() || source.equals(normalized)) return structure;
    return structure.stream()
        .map(
            chapter ->
                new ChapterStructureWriter.Chapter(
                    chapter.key(),
                    chapter.parent(),
                    chapter.position(),
                    chapter.title(),
                    remapOffset(source, normalized, chapter.start()),
                    remapOffset(source, normalized, chapter.end()),
                    chapter.sourceHref(),
                    chapter.confidence()))
        .toList();
  }

  private int remapOffset(String source, String normalized, int codePointOffset) {
    int sourceLength = source.codePointCount(0, source.length());
    int bounded = Math.max(0, Math.min(codePointOffset, sourceLength));
    if (bounded == sourceLength) return normalized.codePointCount(0, normalized.length());
    int charOffset = source.offsetByCodePoints(0, bounded);
    String normalizedPrefix = normalizer.normalize(source.substring(0, charOffset));
    return Math.min(
        normalized.codePointCount(0, normalized.length()),
        normalizedPrefix.codePointCount(0, normalizedPrefix.length()));
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  public record ProcessedText(byte[] normalizedTxt, byte[] chaptersJson, String sha256) {}
}
