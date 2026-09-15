package com.bookrush.analytics.features;

import java.util.*;

/** Flesch formulas explicitly versioned for English and Brazilian Portuguese. */
public final class ReadabilityCalculator {
  private ReadabilityCalculator() {}
  public enum Status { VALID, UNSUPPORTED, INSUFFICIENT_SAMPLE }
  public record Result(Map<String,Double> values, Status status, String warning) {}
  public static Result calculate(String text, String language) {
    if (text == null || text.isBlank()) return new Result(Map.of(), Status.INSUFFICIENT_SAMPLE, "text is required");
    var structural=TextFeatureCalculator.calculate(Objects.requireNonNullElse(text,""),0,1,0,1);
    double words=structural.getOrDefault("struct.word_count",0d);
    double sentences=structural.getOrDefault("struct.sentence_count",0d);
    if (words==0 || sentences==0) return new Result(Map.of(), Status.INSUFFICIENT_SAMPLE, "words and sentences are required");
    double syllables=syllables(text);
    double avg=syllables/words;
    var values=new LinkedHashMap<String,Double>(); values.put("readability.avg_syllables_per_word",avg); values.put("readability.avg_words_per_sentence",words/sentences);
    if ("en".equalsIgnoreCase(language)) values.put("readability.flesch_en",206.835-1.015*(words/sentences)-84.6*avg);
    else if ("pt".equalsIgnoreCase(language)) values.put("readability.flesch_ptbr",248.835-1.015*(words/sentences)-84.6*avg);
    else return new Result(Map.copyOf(values), Status.UNSUPPORTED, "Flesch V1 is defined only for en and pt");
    return new Result(Map.copyOf(values), Status.VALID, null);
  }
  private static double syllables(String text) {
    var m=java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*").matcher(text.toLowerCase(Locale.ROOT)); int total=0;
    while(m.find()) { String w=m.group(); int count=0; boolean vowel=false; for(int i=0;i<w.length();i++){ boolean now="aeiouyáéíóúâêôãõàëïöü".indexOf(w.charAt(i))>=0; if(now&&!vowel)count++; vowel=now; } total+=Math.max(1,count); }
    return total;
  }
}
