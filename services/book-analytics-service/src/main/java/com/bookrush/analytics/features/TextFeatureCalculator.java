package com.bookrush.analytics.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Tier 0 text measurements. They describe text structure and
 * rhythm; they do not infer literary quality, engagement, or recommendation.
 */
public final class TextFeatureCalculator {
  /** Versioned threshold used by {@code short_sentence_*} V2 observations. */
  public static final int V2_SHORT_SENTENCE_MAX_WORDS = 8;

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");
  private static final Pattern SENTENCE = Pattern.compile("[^.!?…。！？]+[.!?…。！？]?");
  private static final Pattern ASCII_ELLIPSIS = Pattern.compile("(?<!\\.)\\.\\.\\.(?!\\.)");
  private static final String PUNCTUATION = "!?,.;:()[]{}\"'“”‘’—–-…！？。，；：（）";

  private TextFeatureCalculator() {}

  public static Map<String, Double> calculate(
      String text, int documentStart, int documentLength, int chapterStart, int chapterLength) {
    if (text == null || text.isEmpty()) throw new IllegalArgumentException("text is required");

    var sentenceLengths = sentenceWordCounts(text);
    var paragraphLengths = paragraphWordCounts(text);
    int words = sentenceLengths.stream().mapToInt(Integer::intValue).sum();
    long wordCodepoints = wordCodepointCount(text);
    int characters = text.codePointCount(0, text.length());
    int sentences = sentenceLengths.size();
    int paragraphs = paragraphLengths.size();
    int questions = countChar(text, '?') + countChar(text, '？');
    int exclamations = countChar(text, '!') + countChar(text, '！');
    int dialogue = countDialogue(text);
    int punctuation = (int) text.codePoints().filter(codePoint -> PUNCTUATION.indexOf(codePoint) >= 0).count();
    int ellipses = countChar(text, '…') + count(ASCII_ELLIPSIS, text);
    int dashes = countChar(text, '—') + countChar(text, '–');

    double sentenceDenominator = Math.max(1, sentences);
    double characterDenominator = Math.max(1, characters);
    double wordDenominator = Math.max(1, words);
    double averageSentenceLength = words / sentenceDenominator;
    double averageParagraphLength = paragraphs == 0 ? 0d : words / (double) paragraphs;
    double sentenceStd = populationStd(sentenceLengths);
    double paragraphStd = populationStd(paragraphLengths);
    double shortSentenceRatio = sentences == 0 ? 0d : sentenceLengths.stream()
        .filter(length -> length <= V2_SHORT_SENTENCE_MAX_WORDS).count() / sentenceDenominator;

    var result = new LinkedHashMap<String, Double>();
    // Stable V1 aliases.
    result.put("word_count", (double) words);
    result.put("character_count", (double) characters);
    result.put("sentence_count", (double) sentences);
    result.put("paragraph_count", (double) paragraphs);
    result.put("average_word_length", wordCodepoints / wordDenominator);
    result.put("average_sentence_length", averageSentenceLength);
    result.put("question_count", (double) questions);
    result.put("question_ratio", questions / sentenceDenominator);
    result.put("exclamation_count", (double) exclamations);
    result.put("exclamation_ratio", exclamations / sentenceDenominator);
    result.put("dialogue_ratio", dialogue / characterDenominator);
    result.put("punctuation_density", punctuation / characterDenominator);
    result.put("estimated_read_time", words / 200.0);
    result.put("relative_position_in_document", relative(documentStart, documentLength));
    result.put("relative_position_in_chapter", relative(chapterStart, chapterLength));

    // V1 namespaced contract aliases.
    result.put("struct.word_count", (double) words);
    result.put("struct.character_count", (double) characters);
    result.put("struct.sentence_count", (double) sentences);
    result.put("struct.paragraph_count", (double) paragraphs);
    result.put("struct.avg_word_chars", wordCodepoints / wordDenominator);
    result.put("struct.avg_sentence_words", averageSentenceLength);
    result.put("struct.avg_paragraph_words", averageParagraphLength);
    result.put("struct.question_count", (double) questions);
    result.put("struct.question_ratio", questions / sentenceDenominator);
    result.put("struct.exclamation_count", (double) exclamations);
    result.put("struct.exclamation_ratio", exclamations / sentenceDenominator);
    result.put("struct.dialogue_ratio", dialogue / characterDenominator);
    result.put("struct.punctuation_density", punctuation / characterDenominator);
    result.put("struct.estimated_read_seconds_220wpm", words / 220.0 * 60.0);
    result.put("struct.relative_document_position", relative(documentStart, documentLength));
    result.put("struct.relative_chapter_position", relative(chapterStart, chapterLength));

    // V2 Tier 0 contract. Formula changes require a new analyzer/version.
    result.put("avg_sentence_length", averageSentenceLength);
    result.put("sentence_length_std", sentenceStd);
    result.put("sentence_length_cv", coefficientOfVariation(sentenceStd, averageSentenceLength));
    result.put("sentence_length_delta_mean", meanAdjacentAbsoluteDelta(sentenceLengths));
    result.put("short_sentence_ratio", shortSentenceRatio);
    result.put("short_sentence_burst", (double) longestShortSentenceBurst(sentenceLengths));
    result.put("paragraph_length_cv", coefficientOfVariation(paragraphStd, averageParagraphLength));
    result.put("ellipsis_density", ellipses / sentenceDenominator);
    result.put("dash_density", dashes / sentenceDenominator);
    result.put("book_relative_position", relative(documentStart, documentLength));
    result.put("chapter_relative_position", relative(chapterStart, chapterLength));
    return Map.copyOf(result);
  }

  private static List<Integer> sentenceWordCounts(String text) {
    var result = new ArrayList<Integer>();
    var matcher = SENTENCE.matcher(text);
    while (matcher.find()) {
      String value = matcher.group();
      if (!value.isBlank()) result.add(wordCount(value));
    }
    return List.copyOf(result);
  }

  private static List<Integer> paragraphWordCounts(String text) {
    var result = new ArrayList<Integer>();
    for (String paragraph : text.split("\\n\\s*\\n", -1)) {
      if (!paragraph.isBlank()) result.add(wordCount(paragraph));
    }
    return List.copyOf(result);
  }

  private static int wordCount(String value) {
    Matcher matcher = WORD.matcher(value);
    int count = 0;
    while (matcher.find()) count++;
    return count;
  }

  private static long wordCodepointCount(String text) {
    Matcher matcher = WORD.matcher(text);
    long count = 0;
    while (matcher.find()) count += text.codePointCount(matcher.start(), matcher.end());
    return count;
  }

  private static int count(Pattern pattern, String text) {
    int count = 0;
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) count++;
    return count;
  }

  private static int countChar(String text, char character) {
    return (int) text.chars().filter(value -> value == character).count();
  }

  private static int countDialogue(String text) {
    int count = 0;
    boolean openQuote = false;
    boolean lineStart = true;
    boolean dashMode = false;
    for (int index = 0; index < text.length();) {
      int codePoint = text.codePointAt(index);
      int width = Character.charCount(codePoint);
      if (codePoint == '\n' || codePoint == '\r') {
        lineStart = true;
        dashMode = false;
        index += width;
        continue;
      }
      if (codePoint == '"' || codePoint == '“' || codePoint == '”' || codePoint == '«' || codePoint == '»') openQuote = !openQuote;
      if (lineStart && (codePoint == '—' || codePoint == '–' || codePoint == '-')) dashMode = true;
      // Keep the V1 feature's historical UTF-16-width behavior. Existing
      // feature rows are reproducible only if this legacy formula remains
      // unchanged; a corrected formula requires a new feature identity.
      if (openQuote || dashMode) count += width;
      lineStart = false;
      index += width;
    }
    return count;
  }

  private static double populationStd(List<Integer> values) {
    if (values.isEmpty()) return 0d;
    double mean = values.stream().mapToDouble(Integer::doubleValue).average().orElse(0d);
    double squareSum = values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum();
    return Math.sqrt(squareSum / values.size());
  }

  private static double coefficientOfVariation(double standardDeviation, double mean) {
    return mean == 0d ? 0d : standardDeviation / mean;
  }

  private static double meanAdjacentAbsoluteDelta(List<Integer> values) {
    if (values.size() < 2) return 0d;
    double total = 0d;
    for (int index = 1; index < values.size(); index++) total += Math.abs(values.get(index) - values.get(index - 1));
    return total / (values.size() - 1);
  }

  private static int longestShortSentenceBurst(List<Integer> values) {
    int current = 0;
    int longest = 0;
    for (int value : values) {
      current = value <= V2_SHORT_SENTENCE_MAX_WORDS ? current + 1 : 0;
      longest = Math.max(longest, current);
    }
    return longest;
  }

  private static double relative(int offset, int length) {
    if (offset < 0 || length <= 0) return 0d;
    return Math.min(1d, Math.max(0d, offset / (double) length));
  }
}
