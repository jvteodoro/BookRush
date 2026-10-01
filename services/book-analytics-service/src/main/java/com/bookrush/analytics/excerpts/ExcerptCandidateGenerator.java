package com.bookrush.analytics.excerpts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sentence-aligned candidate generation with canonical Unicode code-point offsets. The V1 method
 * remains stable for historical replay; V2 adds configurable multi-size windows and optional
 * chapter boundaries.
 */
public final class ExcerptCandidateGenerator {
  private static final Pattern SENTENCE = Pattern.compile("[^.!?…。！？]+[.!?…。！？]?");
  private static final Pattern WORD =
      Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

  private ExcerptCandidateGenerator() {}

  /** Historical V1 generator. Do not change its semantics or version identity. */
  public static List<Candidate> generate(
      String text, int targetWords, int strideSentences, String version) {
    if (text == null || text.isBlank()) return List.of();
    var sentences = sentences(text, 0, codePointLength(text));
    var result = new ArrayList<Candidate>();
    int stride = Math.max(1, strideSentences);
    int limit = Math.max(1, targetWords);
    for (int i = 0; i < sentences.size(); i += stride) {
      int words = 0;
      int end = i;
      while (end < sentences.size() && (end == i || words < limit)) {
        words += sentences.get(end).words();
        end++;
      }
      var first = sentences.get(i);
      var last = sentences.get(end - 1);
      String value = slice(text, first.startCodepoint(), last.endCodepoint());
      if (isBoilerplate(value)) continue;
      result.add(
          new Candidate(
              first.startCodepoint(),
              last.endCodepoint(),
              value,
              words,
              end - i,
              hash(value),
              version));
    }
    return result.stream().distinct().toList();
  }

  /**
   * Generates V2 candidates independently inside each chapter unless the configuration explicitly
   * permits cross-chapter windows. Repeating this method with the same normalized text and
   * configuration returns candidates in the same order and with the same hashes/offsets.
   */
  public static List<V2Candidate> generateV2(
      String text, List<ChapterBoundary> chapters, V2Config config) {
    Objects.requireNonNull(config, "config is required");
    if (text == null || text.isBlank()) return List.of();

    int textLength = codePointLength(text);
    var sections = sections(chapters, textLength, config.crossChapter());
    var candidates = new ArrayList<V2Candidate>();
    for (var section : sections) {
      var sentenceList = sentences(text, section.startCodepoint(), section.endCodepoint());
      for (var profile : config.windowProfiles()) {
        candidates.addAll(windows(text, sentenceList, section.chapterId(), profile, config));
      }
    }

    candidates.sort(
        Comparator.comparingInt(V2Candidate::startCodepoint)
            .thenComparingInt(V2Candidate::endCodepoint)
            .thenComparing(c -> c.chapterId(), Comparator.nullsFirst(Comparator.naturalOrder())));

    var seenIntervals = new HashSet<String>();
    var seenTexts = new HashSet<String>();
    var result = new ArrayList<V2Candidate>();
    for (var candidate : candidates) {
      String interval = candidate.startCodepoint() + ":" + candidate.endCodepoint();
      if (!seenIntervals.add(interval) || !seenTexts.add(normalize(candidate.text()))) continue;
      result.add(candidate);
    }
    return List.copyOf(result);
  }

  private static List<V2Candidate> windows(
      String text,
      List<Sentence> sentences,
      UUID chapterId,
      WindowProfile profile,
      V2Config config) {
    var result = new ArrayList<V2Candidate>();
    for (int start = 0; start < sentences.size(); start += profile.sentenceStride()) {
      int words = 0;
      int end = start;
      while (end < sentences.size() && words < profile.targetWords()) {
        words += sentences.get(end).words();
        end++;
      }
      if (end == start || words < config.minWords() || words > config.maxWords()) continue;
      var first = sentences.get(start);
      var last = sentences.get(end - 1);
      String value = slice(text, first.startCodepoint(), last.endCodepoint());
      if (isBoilerplate(value) || !containsLexicalContent(value)) continue;
      result.add(
          new V2Candidate(
              chapterId,
              first.startCodepoint(),
              last.endCodepoint(),
              value,
              words,
              end - start,
              hash(value),
              config.version()));
    }
    return result;
  }

  private static List<Section> sections(
      List<ChapterBoundary> chapters, int textLength, boolean crossChapter) {
    if (crossChapter || chapters == null || chapters.isEmpty())
      return List.of(new Section(null, 0, textLength));
    var ordered = new ArrayList<>(chapters);
    ordered.sort(Comparator.comparingInt(ChapterBoundary::startCodepoint));
    int previousEnd = 0;
    for (var chapter : ordered) {
      if (chapter.startCodepoint() < previousEnd || chapter.endCodepoint() > textLength) {
        throw new IllegalArgumentException(
            "chapters must be ordered, non-overlapping and inside the text");
      }
      previousEnd = chapter.endCodepoint();
    }
    return ordered.stream()
        .map(c -> new Section(c.chapterId(), c.startCodepoint(), c.endCodepoint()))
        .toList();
  }

  private static List<Sentence> sentences(String text, int startCodepoint, int endCodepoint) {
    int startUtf16 = toUtf16(text, startCodepoint);
    int endUtf16 = toUtf16(text, endCodepoint);
    String section = text.substring(startUtf16, endUtf16);
    var result = new ArrayList<Sentence>();
    var matcher = SENTENCE.matcher(section);
    while (matcher.find()) {
      String raw = matcher.group();
      int leadingUtf16 = leadingUtf16(raw);
      int trailingUtf16 = trailingUtf16(raw);
      if (leadingUtf16 == trailingUtf16) continue;
      int sentenceStart =
          startCodepoint + section.codePointCount(0, matcher.start() + leadingUtf16);
      int sentenceEnd = startCodepoint + section.codePointCount(0, matcher.start() + trailingUtf16);
      String value = slice(text, sentenceStart, sentenceEnd);
      result.add(new Sentence(sentenceStart, sentenceEnd, words(value)));
    }
    return result;
  }

  private static int leadingUtf16(String value) {
    int index = 0;
    while (index < value.length()) {
      int codePoint = value.codePointAt(index);
      if (!Character.isWhitespace(codePoint)) break;
      index += Character.charCount(codePoint);
    }
    return index;
  }

  private static int trailingUtf16(String value) {
    int index = value.length();
    while (index > 0) {
      int codePoint = value.codePointBefore(index);
      if (!Character.isWhitespace(codePoint)) break;
      index -= Character.charCount(codePoint);
    }
    return index;
  }

  private static int words(String value) {
    var matcher = WORD.matcher(value);
    int count = 0;
    while (matcher.find()) count++;
    return count;
  }

  private static boolean containsLexicalContent(String value) {
    return words(value) > 0;
  }

  private static boolean isBoilerplate(String value) {
    var normalized = value.strip().toLowerCase(Locale.ROOT);
    return normalized.matches("(?s)^(contents|table of contents|index)\\b.*")
        || normalized.length() < 20;
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }

  private static String slice(String text, int startCodepoint, int endCodepoint) {
    return text.substring(toUtf16(text, startCodepoint), toUtf16(text, endCodepoint));
  }

  private static int toUtf16(String text, int codepoint) {
    return text.offsetByCodePoints(0, codepoint);
  }

  private static int codePointLength(String text) {
    return text.codePointCount(0, text.length());
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Sentence(int startCodepoint, int endCodepoint, int words) {}

  private record Section(UUID chapterId, int startCodepoint, int endCodepoint) {}

  public record Candidate(
      int startCodepoint,
      int endCodepoint,
      String text,
      int wordCount,
      int sentenceCount,
      String textSha256,
      String generatorVersion) {}

  public record ChapterBoundary(UUID chapterId, int startCodepoint, int endCodepoint) {
    public ChapterBoundary {
      Objects.requireNonNull(chapterId, "chapterId is required");
      if (startCodepoint < 0 || endCodepoint <= startCodepoint) {
        throw new IllegalArgumentException("chapter must have a non-empty half-open interval");
      }
    }
  }

  public record WindowProfile(int targetWords, int sentenceStride) {
    public WindowProfile {
      if (targetWords < 1 || sentenceStride < 1) {
        throw new IllegalArgumentException("targetWords and sentenceStride must be positive");
      }
    }
  }

  public record V2Config(
      int minWords,
      List<WindowProfile> windowProfiles,
      int maxWords,
      boolean crossChapter,
      String version) {
    public V2Config {
      if (minWords < 1 || maxWords < minWords)
        throw new IllegalArgumentException("invalid V2 word bounds");
      windowProfiles =
          List.copyOf(Objects.requireNonNull(windowProfiles, "windowProfiles is required"));
      if (windowProfiles.isEmpty())
        throw new IllegalArgumentException("at least one V2 window profile is required");
      if (windowProfiles.stream().anyMatch(profile -> profile.targetWords() > maxWords)) {
        throw new IllegalArgumentException("targetWords must not exceed maxWords");
      }
      if (version == null || version.isBlank())
        throw new IllegalArgumentException("version is required");
    }

    public static V2Config pilotDefaults(String version) {
      return new V2Config(
          60,
          List.of(new WindowProfile(90, 1), new WindowProfile(140, 2), new WindowProfile(200, 3)),
          250,
          false,
          version);
    }
  }

  public record V2Candidate(
      UUID chapterId,
      int startCodepoint,
      int endCodepoint,
      String text,
      int wordCount,
      int sentenceCount,
      String textSha256,
      String generatorVersion) {
    public V2Candidate {
      if (startCodepoint < 0 || endCodepoint <= startCodepoint || text == null || text.isBlank()) {
        throw new IllegalArgumentException(
            "candidate must contain a non-empty half-open interval and text");
      }
    }
  }
}
