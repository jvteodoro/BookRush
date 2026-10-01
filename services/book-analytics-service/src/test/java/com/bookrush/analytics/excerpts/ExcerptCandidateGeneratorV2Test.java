package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExcerptCandidateGeneratorV2Test {
  @Test
  void generatesStableMultiSizeSentenceAlignedWindowsInsideChapters() {
    String chapterOne = sentences("Um", 16);
    String chapterTwo = sentences("Dois", 16);
    String text = "Prefácio.\n\n" + chapterOne + "\n\n" + chapterTwo;
    int chapterOneStart = cp(text, text.indexOf(chapterOne));
    int chapterOneEnd = chapterOneStart + chapterOne.codePointCount(0, chapterOne.length());
    int chapterTwoStart = cp(text, text.indexOf(chapterTwo));
    int chapterTwoEnd = chapterTwoStart + chapterTwo.codePointCount(0, chapterTwo.length());
    var one = UUID.fromString("10000000-0000-4000-8000-000000000001");
    var two = UUID.fromString("10000000-0000-4000-8000-000000000002");
    var config =
        new ExcerptCandidateGenerator.V2Config(
            10,
            List.of(
                new ExcerptCandidateGenerator.WindowProfile(10, 1),
                new ExcerptCandidateGenerator.WindowProfile(14, 2)),
            18,
            false,
            "sentence-window-v2");

    var first =
        ExcerptCandidateGenerator.generateV2(
            text,
            List.of(
                new ExcerptCandidateGenerator.ChapterBoundary(one, chapterOneStart, chapterOneEnd),
                new ExcerptCandidateGenerator.ChapterBoundary(two, chapterTwoStart, chapterTwoEnd)),
            config);
    var second =
        ExcerptCandidateGenerator.generateV2(
            text,
            List.of(
                new ExcerptCandidateGenerator.ChapterBoundary(one, chapterOneStart, chapterOneEnd),
                new ExcerptCandidateGenerator.ChapterBoundary(two, chapterTwoStart, chapterTwoEnd)),
            config);

    assertEquals(first, second);
    assertFalse(first.isEmpty());
    assertTrue(
        first.stream()
            .allMatch(candidate -> candidate.wordCount() >= 10 && candidate.wordCount() <= 18));
    assertTrue(
        first.stream()
            .allMatch(
                candidate ->
                    candidate.chapterId().equals(one) || candidate.chapterId().equals(two)));
    assertTrue(
        first.stream()
            .allMatch(
                candidate ->
                    candidate
                        .text()
                        .equals(
                            slice(text, candidate.startCodepoint(), candidate.endCodepoint()))));
    assertTrue(
        first.stream()
            .allMatch(candidate -> candidate.textSha256().equals(sha256(candidate.text()))));
    assertTrue(first.stream().noneMatch(candidate -> candidate.text().contains("Prefácio")));
  }

  @Test
  void permitsCrossChapterWindowsOnlyWhenConfigured() {
    String text = sentences("A", 8) + "\n\n" + sentences("B", 8);
    int split = cp(text, text.indexOf("B um"));
    var config =
        new ExcerptCandidateGenerator.V2Config(
            12,
            List.of(new ExcerptCandidateGenerator.WindowProfile(14, 1)),
            30,
            true,
            "sentence-window-v2");
    var candidates =
        ExcerptCandidateGenerator.generateV2(
            text,
            List.of(
                new ExcerptCandidateGenerator.ChapterBoundary(UUID.randomUUID(), 0, split),
                new ExcerptCandidateGenerator.ChapterBoundary(
                    UUID.randomUUID(), split, text.codePointCount(0, text.length()))),
            config);

    assertTrue(candidates.stream().anyMatch(candidate -> candidate.chapterId() == null));
  }

  @Test
  void rejectsInvalidChapterBoundariesAndInvalidConfig() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExcerptCandidateGenerator.V2Config(20, List.of(), 10, false, "v2"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ExcerptCandidateGenerator.generateV2(
                "One. Two.",
                List.of(new ExcerptCandidateGenerator.ChapterBoundary(UUID.randomUUID(), 0, 100)),
                ExcerptCandidateGenerator.V2Config.pilotDefaults("v2")));
  }

  private static String sentences(String prefix, int count) {
    var builder = new StringBuilder();
    for (int index = 1; index <= count; index++) {
      builder.append(prefix).append(" um dois três quatro cinco seis sete oito nove dez.");
      if (index < count) builder.append(' ');
    }
    return builder.toString();
  }

  private static int cp(String value, int utf16) {
    return value.codePointCount(0, utf16);
  }

  private static String slice(String text, int start, int end) {
    return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
