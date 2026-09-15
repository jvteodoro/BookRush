package com.bookrush.analytics.features;

import java.util.*;

/** Add-alpha unigram model with deterministic OOV surprisal. */
public final class CorpusFrequencyModel {
  private final String language, snapshot; private final long total; private final double alpha; private final Map<String,Long> counts;
  public CorpusFrequencyModel(String language,String snapshot,Map<String,Long> counts,long total,double alpha){if(total<0||alpha<=0)throw new IllegalArgumentException("invalid corpus model");this.language=Objects.requireNonNull(language);this.snapshot=Objects.requireNonNull(snapshot);this.counts=Map.copyOf(counts);this.total=total;this.alpha=alpha;}
  public double surprisal(String token){long v=counts.getOrDefault(token.toLowerCase(Locale.ROOT),0L); double p=(v+alpha)/(total+alpha*counts.size()); return -Math.log(p);}
  public Stats summarize(Collection<String> tokens){var values=tokens.stream().filter(t->!t.isBlank()).map(this::surprisal).sorted().toList(); if(values.isEmpty())return new Stats(0,0,0,0); return new Stats(values.stream().mapToDouble(x->x).average().orElse(0),values.get(values.size()/2),values.get((int)Math.ceil(values.size()*.9)-1),values.size());}
  public record Stats(double mean,double median,double p90,int sampleSize) {}
  public String language(){return language;} public String snapshot(){return snapshot;}
}
