package com.bookrush.recommendationservice.ranking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic epsilon/slot exploration; not a contextual bandit. */
public final class ExplorationPolicy {
  private final Config config;

  public ExplorationPolicy(Config config) { this.config = config; }

  public String policyVersion() { return config.version(); }

  public List<Choice> choose(List<String> rankedIds, Set<String> alreadySelected,
      long requestSeed, int feedSize) {
    if (!config.enabled() || rankedIds == null || feedSize < 1) return List.of();
    var selected = alreadySelected == null ? Set.<String>of() : alreadySelected;
    int target = Math.min(config.quota(), Math.max(1, (int) Math.ceil(feedSize * config.epsilon())));
    var result = new ArrayList<Choice>();
    var seen = new HashSet<String>(selected);
    for (String id : rankedIds) {
      if (id == null || !seen.add(id)) continue;
      double probability = unit(requestSeed, id);
      if (probability <= config.epsilon() && result.size() < target) {
        result.add(new Choice(id, config.version(), "epsilon-exploration", probability));
      }
    }
    return List.copyOf(result);
  }

  private static double unit(long seed, String id) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((seed + ":" + id).getBytes(StandardCharsets.UTF_8));
      long value = 0;
      for (int i = 0; i < 8; i++) value = (value << 8) | (digest[i] & 0xffL);
      return (value >>> 1) / (double) Long.MAX_VALUE;
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required for exploration", exception);
    }
  }

  public record Choice(String bookId, String policyVersion, String reason, double draw) { }

  public record Config(String version, boolean enabled, double epsilon, int quota) {
    public Config {
      if (version == null || version.isBlank() || epsilon < 0 || epsilon > 1 || quota < 0) {
        throw new IllegalArgumentException("invalid exploration configuration");
      }
    }
  }
}
