package com.bookrush.recommendationservice.retrieval;

import com.bookrush.recommendationservice.taste.UserTasteProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic source union used by PFV1. Providers own how each source is
 * obtained; this class owns quotas, cosine scoring, de-duplication and replay.
 */
public final class CandidateUnionBuilder {
  private final Config config;

  public CandidateUnionBuilder(Config config) {
    this.config = Objects.requireNonNull(config);
  }

  public CandidateSet build(UserTasteProfile profile, List<Source> sources) {
    var byId = new LinkedHashMap<String, Map<String, Object>>();
    var sourceNames = new LinkedHashMap<String, LinkedHashSet<String>>();
    var orderedSources = sources == null ? List.<Source>of() : sources.stream()
        .filter(Objects::nonNull).sorted(Comparator.comparingInt((Source source) -> sourcePriority(source.name()))
            .thenComparing(Source::name)).toList();
    for (Source source : orderedSources) {
      var candidates = source.candidates().stream()
          .filter(candidate -> candidate != null && candidate.book().get("id") != null)
          .map(candidate -> scored(candidate, source.name(), profile))
          .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
              .thenComparing(candidate -> String.valueOf(candidate.book().get("id"))))
          .limit(config.quota(source.name()))
          .toList();
      for (ScoredCandidate candidate : candidates) {
        String id = String.valueOf(candidate.book().get("id"));
        byId.putIfAbsent(id, candidate.book());
        sourceNames.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(source.name());
        if (byId.size() >= config.maxCandidates()) break;
      }
      if (byId.size() >= config.maxCandidates()) break;
    }
    var sourceMetadata = new LinkedHashMap<String, List<String>>();
    sourceNames.forEach((id, names) -> sourceMetadata.put(id, names.stream().sorted().toList()));
    return new CandidateSet(config.version(), config.hash(), "union",
        new ArrayList<>(byId.values()), sourceMetadata);
  }

  private static int sourcePriority(String source) {
    return switch (source) {
      case "semantic" -> 0;
      case "global" -> 1;
      case "trending" -> 2;
      case "exploration" -> 3;
      default -> 4;
    };
  }

  private ScoredCandidate scored(Candidate candidate, String source, UserTasteProfile profile) {
    if (!"semantic".equals(source) || profile == null
        || profile.status() != UserTasteProfile.Status.READY) {
      return new ScoredCandidate(candidate.book(), candidate.score());
    }
    Object raw = candidate.book().get("embedding");
    if (!(raw instanceof List<?> vector)) return new ScoredCandidate(candidate.book(), candidate.score());
    double cosine = cosine(profile.vector(), vector);
    return new ScoredCandidate(candidate.book(), Double.isFinite(cosine) ? cosine : candidate.score());
  }

  static double cosine(List<Double> left, List<?> right) {
    if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0;
    double dot = 0, leftNorm = 0, rightNorm = 0;
    for (int i = 0; i < left.size(); i++) {
      if (!(right.get(i) instanceof Number number) || left.get(i) == null) return 0;
      double a = left.get(i), b = number.doubleValue();
      if (!Double.isFinite(a) || !Double.isFinite(b)) return 0;
      dot += a * b;
      leftNorm += a * a;
      rightNorm += b * b;
    }
    return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
  }

  private record ScoredCandidate(Map<String, Object> book, double score) { }

  public record Candidate(Map<String, Object> book, double score) {
    public Candidate {
      book = Map.copyOf(Objects.requireNonNull(book));
      score = Double.isFinite(score) ? score : 0;
    }
  }

  public record Source(String name, List<Candidate> candidates) {
    public Source {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("source name required");
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
  }

  public record Config(String version, int maxCandidates, int semanticQuota, int globalQuota,
      int trendingQuota, int explorationQuota) {
    public Config {
      if (version == null || version.isBlank() || maxCandidates < 1
          || semanticQuota < 0 || globalQuota < 0 || trendingQuota < 0 || explorationQuota < 0) {
        throw new IllegalArgumentException("invalid candidate union configuration");
      }
    }

    int quota(String source) {
      return switch (source) {
        case "semantic" -> semanticQuota;
        case "global" -> globalQuota;
        case "trending" -> trendingQuota;
        case "exploration" -> explorationQuota;
        default -> maxCandidates;
      };
    }

    String hash() {
      String value = version + "|" + maxCandidates + "|" + semanticQuota + "|" + globalQuota
          + "|" + trendingQuota + "|" + explorationQuota;
      try {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException("SHA-256 is required for candidate configuration", exception);
      }
    }
  }
}
