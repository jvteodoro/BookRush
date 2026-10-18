package com.bookrush.analytics.excerpts;

import java.util.*;

/** Versioned cheap pruning with an auditable reason for every rejection. */
public final class CandidatePruner {
  private CandidatePruner() {}

  public static Result prune(List<ExcerptCandidateGenerator.Candidate> candidates, Config config) {
    var accepted = new ArrayList<ExcerptCandidateGenerator.Candidate>();
    var rejected = new ArrayList<Rejected>();
    var seen = new HashSet<String>();
    for (var c : candidates) {
      String reason = null;
      if (c.wordCount() < config.minWords()) reason = "TOO_SHORT";
      else if (c.wordCount() > config.maxWords()) reason = "TOO_LONG";
      else if (c.text().isBlank()) reason = "EMPTY";
      else if (isBoilerplate(c.text())) reason = "BOILERPLATE";
      else if (seen.contains(normalize(c.text()))) reason = "DUPLICATE";
      else if (nearDuplicate(c.text(), seen)) reason = "NEAR_DUPLICATE";
      else seen.add(normalize(c.text()));
      if (reason == null) accepted.add(c);
      else rejected.add(new Rejected(c, reason));
    }
    return new Result(List.copyOf(accepted), List.copyOf(rejected), config.version());
  }

  public static V2Result pruneV2(
      List<ExcerptCandidateGenerator.V2Candidate> candidates, Config config) {
    var accepted = new ArrayList<ExcerptCandidateGenerator.V2Candidate>();
    var rejected = new ArrayList<V2Rejected>();
    var seen = new HashSet<String>();
    for (var c : candidates) {
      String reason = null;
      if (c.wordCount() < config.minWords()) reason = "TOO_SHORT";
      else if (c.wordCount() > config.maxWords()) reason = "TOO_LONG";
      else if (c.text().isBlank()) reason = "EMPTY";
      else if (isBoilerplate(c.text())) reason = "BOILERPLATE";
      else if (seen.contains(normalize(c.text()))) reason = "DUPLICATE";
      else if (nearDuplicate(c.text(), seen)) reason = "NEAR_DUPLICATE";
      else seen.add(normalize(c.text()));
      if (reason == null) accepted.add(c);
      else rejected.add(new V2Rejected(c, reason));
    }
    return new V2Result(List.copyOf(accepted), List.copyOf(rejected), config.version());
  }

  private static String normalize(String v) {
    return v.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }

  private static boolean isBoilerplate(String value) {
    var normalized = normalize(value);
    return normalized.matches("(?:table of contents|contents|index|document|sumário|indice)");
  }

  private static boolean nearDuplicate(String value, Set<String> seen) {
    var candidate = tokens(value);
    if (candidate.size() < 4) return false;
    return seen.stream().anyMatch(previous -> {
      var words = tokens(previous);
      var intersection = candidate.stream().filter(words::contains).count();
      var union = candidate.size() + words.size() - intersection;
      return union > 0 && ((double) intersection / union) >= 0.85d;
    });
  }

  private static Set<String> tokens(String value) {
    var tokens = new HashSet<String>();
    var matcher =
        java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS)
            .matcher(normalize(value));
    while (matcher.find()) tokens.add(matcher.group());
    return tokens;
  }

  public record Config(int minWords, int maxWords, String version) {
    public Config {
      if (minWords < 0 || maxWords < minWords)
        throw new IllegalArgumentException("invalid pruning bounds");
    }
  }

  public record Rejected(ExcerptCandidateGenerator.Candidate candidate, String reason) {}

  public record Result(
      List<ExcerptCandidateGenerator.Candidate> accepted,
      List<Rejected> rejected,
      String configVersion) {}

  public record V2Rejected(ExcerptCandidateGenerator.V2Candidate candidate, String reason) {}

  public record V2Result(
      List<ExcerptCandidateGenerator.V2Candidate> accepted,
      List<V2Rejected> rejected,
      String configVersion) {}
}
