package com.bookrush.analytics.features;

import java.util.*;
import java.util.regex.Pattern;

/** Transparent statistical features; formulas are intentionally documented and versioned. */
public final class StatisticalFeatureCalculator {
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");
  private StatisticalFeatureCalculator() {}
  public static Map<String, Double> calculate(String text, Set<String> stopwords) {
    var words = new ArrayList<String>(); var m=WORD.matcher(text.toLowerCase(Locale.ROOT));
    while(m.find()) words.add(m.group());
    if (words.isEmpty()) return Map.of();
    var frequencies = new HashMap<String,Integer>(); words.forEach(w -> frequencies.merge(w,1,Integer::sum));
    long rare = words.stream().filter(w -> frequencies.get(w) == 1).count();
    long stops = stopwords == null ? 0 : words.stream().filter(stopwords::contains).count();
    int sentences = Math.max(1, (int) text.codePoints().filter(c -> c=='.'||c=='!'||c=='?'||c=='。'||c=='！'||c=='？').count());
    double syllableEstimate = words.stream().mapToInt(StatisticalFeatureCalculator::syllables).sum();
    double resultReadability = 206.835 - 1.015 * (words.size() / (double) sentences) - 84.6 * (syllableEstimate / words.size());
    return Map.of("lexical_diversity", frequencies.size() / (double) words.size(),
        "rare_word_ratio", rare / (double) words.size(),
        "stopword_ratio", stops / (double) words.size(),
        "lexical_complexity", words.stream().mapToInt(String::length).average().orElse(0),
        "readability_flesch_estimate", resultReadability);
  }
  private static int syllables(String word) { int n=0; boolean vowel=false; for (int i=0;i<word.length();i++) { boolean now="aeiouyáéíóúâêôãõàëïöü".indexOf(word.charAt(i))>=0; if(now&&!vowel)n++; vowel=now; } return Math.max(1,n); }
}
