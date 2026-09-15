package com.bookrush.analytics.features;

import java.util.*;
import java.util.regex.Pattern;

/** Versioned lexical metrics. Short samples are marked unstable instead of hidden. */
public final class LexicalDiversityCalculator {
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");
  private LexicalDiversityCalculator() {}
  public enum Status { VALID, INSUFFICIENT_SAMPLE }
  public record Result(Map<String, Double> values, Status status, int sampleSize, List<String> warnings) {}
  public static Result calculate(String text) {
    var tokens = new ArrayList<String>(); var m=WORD.matcher(Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT));
    while(m.find()) tokens.add(m.group());
    int n=tokens.size();
    if (n==0) return new Result(Map.of(), Status.INSUFFICIENT_SAMPLE, 0, List.of("no word tokens"));
    var freq=new HashMap<String,Integer>(); tokens.forEach(t -> freq.merge(t,1,Integer::sum));
    int v=freq.size(); long hapax=freq.values().stream().filter(x -> x==1).count();
    double m2=freq.values().stream().mapToDouble(x -> (double)x*x).sum();
    double yule=10000d*(m2-n)/(n*(double)n);
    var values=new LinkedHashMap<String,Double>();
    values.put("lex.ttr", v/(double)n);
    values.put("lex.herdan_c", n>1 && v>0 ? Math.log(v)/Math.log(n) : 0d);
    values.put("lex.yule_k", yule);
    values.put("lex.hapax_ratio", hapax/(double)n);
    values.put("lex.lemma_type_count", (double)v); // replaced by spaCy lemma count when available
    var status=n < 50 ? Status.INSUFFICIENT_SAMPLE : Status.VALID;
    var warnings=n < 50 ? List.of("sample has fewer than 50 tokens; compare cautiously") : List.<String>of();
    return new Result(Map.copyOf(values), status, n, warnings);
  }
}
