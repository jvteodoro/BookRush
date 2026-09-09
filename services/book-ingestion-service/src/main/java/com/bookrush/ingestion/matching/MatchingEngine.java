package com.bookrush.ingestion.matching;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure deterministic matcher; candidates are prepared locally and never trigger HTTP calls. */
public final class MatchingEngine {
  private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N} ]");

  public Decision decide(Input input, List<Candidate> candidates) {
    var ranked = candidates.stream().map(c -> new ScoredCandidate(c, score(input, c))).sorted(
        Comparator.comparingDouble(ScoredCandidate::score).reversed().thenComparing(c -> c.candidate().stableId())).limit(20).toList();
    if (ranked.isEmpty()) return new Decision("NO_MATCH", null, 0, 0, ranked);
    double top = ranked.get(0).score();
    double second = ranked.size() > 1 ? ranked.get(1).score() : 0;
    String decision = top >= .95 && top-second >= .10 && !ranked.get(0).candidate().contradiction() && sufficient(input)
        ? "AUTO_ACCEPTED" : top >= .80 ? "REVIEW_REQUIRED" : "NO_MATCH";
    return new Decision(decision, ranked.get(0).candidate().stableId(), top, top-second, ranked);
  }

  private double score(Input i, Candidate c) {
    double title = similarity(i.title(), c.title());
    double contributor = similarity(i.contributor(), c.contributor());
    double language = i.language() == null || c.language() == null ? 0 : normalize(i.language()).equals(normalize(c.language())) ? 1 : 0;
    return Math.round((.55 * title + .35 * contributor + .10 * language) * 10000d) / 10000d;
  }

  private boolean sufficient(Input input) { return normalize(input.title()).length() >= 4 && normalize(input.contributor()).length() >= 3; }

  private double similarity(String a, String b) {
    if (a == null || b == null) return 0;
    var left = java.util.Arrays.stream(normalize(a).split(" ")).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
    var right = java.util.Arrays.stream(normalize(b).split(" ")).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
    if (left.isEmpty() || right.isEmpty()) return 0;
    long intersection = left.stream().filter(right::contains).count();
    return (double) intersection / (left.size() + right.size() - intersection);
  }

  public static String normalize(String value) {
    if (value == null) return "";
    return PUNCTUATION.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll(" ")
        .replaceAll("\\s+", " ").trim();
  }

  public record Input(String title, String contributor, String language) {}
  public record Candidate(String stableId, String title, String contributor, String language, boolean contradiction) {}
  public record ScoredCandidate(Candidate candidate, double score) {}
  public record Decision(String status, String selectedId, double score, double margin, List<ScoredCandidate> candidates) {}
}
