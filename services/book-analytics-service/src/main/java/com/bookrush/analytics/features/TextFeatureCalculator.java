package com.bookrush.analytics.features;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Versioned, language-neutral baseline features. Values are observations, not editorial truth. */
public final class TextFeatureCalculator {
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");
  private static final Pattern SENTENCE = Pattern.compile("[^.!?…。！？]+[.!?…。！？]?");
  private TextFeatureCalculator() {}

  public static Map<String, Double> calculate(String text, int documentStart, int documentLength, int chapterStart, int chapterLength) {
    if (text == null || text.isEmpty()) throw new IllegalArgumentException("text is required");
    int words = 0; long wordChars = 0;
    var matcher = WORD.matcher(text);
    while (matcher.find()) { words++; wordChars += text.codePointCount(matcher.start(), matcher.end()); }
    int characters = text.codePointCount(0, text.length());
    int sentences = count(SENTENCE, text);
    int paragraphs = (int) java.util.Arrays.stream(text.split("\\n\\s*\\n", -1)).filter(s -> !s.isBlank()).count();
    int questions = countChar(text, '?') + countChar(text, '？');
    int exclamations = countChar(text, '!') + countChar(text, '！');
    int dialogue = countDialogue(text);
    int punctuation = (int) text.codePoints().filter(c -> "!?,.;:()[]{}\"'“”‘’—–-…！？。，；：（）".indexOf(c) >= 0).count();
    double sentenceDenom = Math.max(1, sentences);
    double charsDenom = Math.max(1, characters);
    double wordsDenom = Math.max(1, words);
    var result = new LinkedHashMap<String, Double>();
    result.put("word_count", (double) words);
    result.put("character_count", (double) characters);
    result.put("sentence_count", (double) sentences);
    result.put("paragraph_count", (double) paragraphs);
    result.put("average_word_length", wordChars / wordsDenom);
    result.put("average_sentence_length", words / sentenceDenom);
    result.put("question_count", (double) questions);
    result.put("question_ratio", questions / sentenceDenom);
    result.put("exclamation_count", (double) exclamations);
    result.put("exclamation_ratio", exclamations / sentenceDenom);
    result.put("dialogue_ratio", dialogue / charsDenom);
    result.put("punctuation_density", punctuation / charsDenom);
    result.put("estimated_read_time", words / 200.0);
    result.put("relative_position_in_document", relative(documentStart, documentLength));
    result.put("relative_position_in_chapter", relative(chapterStart, chapterLength));
    return Map.copyOf(result);
  }

  private static int count(Pattern pattern, String text) { var m = pattern.matcher(text); int n=0; while(m.find()) if(!m.group().isBlank()) n++; return n; }
  private static int countChar(String text, char c) { return (int) text.chars().filter(x -> x == c).count(); }
  private static int countDialogue(String text) { int n=0; boolean open=false; for (int i=0;i<text.length();i++) { char c=text.charAt(i); if (c=='"'||c=='“'||c=='”'||c=='«'||c=='»') open=!open; else if (open) n += Character.isWhitespace(c) ? 0 : Character.charCount(c); } return n; }
  private static double relative(int offset, int length) { if (offset < 0 || length <= 0) return 0d; return Math.min(1d, Math.max(0d, offset / (double) length)); }
}
