package com.bookrush.analytics.excerpts;

import com.bookrush.analytics.storage.TextAssetReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Cheap sentence-window generator using Unicode code-point offsets. */
public final class ExcerptCandidateGenerator {
  private static final Pattern SENTENCE = Pattern.compile("[^.!?…。！？]+[.!?…。！？]?");
  private ExcerptCandidateGenerator() {}
  public static List<Candidate> generate(String text, int targetWords, int strideSentences, String version) {
    if (text == null || text.isBlank()) return List.of();
    var sentences = new ArrayList<Sentence>(); var matcher = SENTENCE.matcher(text);
    while (matcher.find()) {
      String value = matcher.group().trim(); if (value.isBlank()) continue;
      int start = text.codePointCount(0, matcher.start()) + leadingCodePoints(matcher.group());
      int end = start + value.codePointCount(0, value.length());
      sentences.add(new Sentence(start,end,value,words(value)));
    }
    var result = new ArrayList<Candidate>();
    int stride=Math.max(1,strideSentences), limit=Math.max(1,targetWords);
    for (int i=0; i<sentences.size(); i+=stride) {
      int words=0, end=i; while(end<sentences.size() && (end==i || words<limit)) { words += sentences.get(end).words; end++; }
      String value = text.substring(toUtf16(text,sentences.get(i).start), toUtf16(text,sentences.get(end-1).end));
      if (isBoilerplate(value)) continue;
      result.add(new Candidate(sentences.get(i).start, sentences.get(end-1).end, value, words, end-i, hash(value), version));
    }
    return result.stream().distinct().toList();
  }
  private static int leadingCodePoints(String value) { int i=0; while(i<value.length() && Character.isWhitespace(value.charAt(i))) i++; return value.codePointCount(0,i); }
  private static int toUtf16(String text,int cp) { return text.offsetByCodePoints(0,cp); }
  private static int words(String value) { var m=Pattern.compile("[\\p{L}\\p{N}]+",Pattern.UNICODE_CHARACTER_CLASS).matcher(value); int n=0; while(m.find()) n++; return n; }
  private static boolean isBoilerplate(String value) { var normalized=value.strip().toLowerCase(Locale.ROOT); return normalized.matches("(?s)^(contents|table of contents|index)\\b.*") || normalized.length()<20; }
  private static String hash(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
  private record Sentence(int start,int end,String text,int words) {}
  public record Candidate(int startCodepoint,int endCodepoint,String text,int wordCount,int sentenceCount,String textSha256,String generatorVersion) {}
}
