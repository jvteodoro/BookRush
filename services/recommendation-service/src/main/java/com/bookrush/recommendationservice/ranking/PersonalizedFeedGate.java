package com.bookrush.recommendationservice.ranking;

import com.bookrush.recommendationservice.taste.UserTasteProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Decides whether personalized serving is safe without making it a hard dependency. */
public final class PersonalizedFeedGate {
  private final Properties properties;
  public PersonalizedFeedGate(Properties properties) { this.properties = properties; }

  public Decision decide(UserTasteProfile profile, boolean behaviorAvailable, boolean snapshotAvailable) {
    return decide(profile, behaviorAvailable, snapshotAvailable, "");
  }

  public Decision decide(UserTasteProfile profile, boolean behaviorAvailable, boolean snapshotAvailable,
      String subject) {
    if (properties.mode() == Mode.OFF) return new Decision(Path.FALLBACK, "disabled");
    if (properties.mode() == Mode.SHADOW) return new Decision(Path.SHADOW, "shadow");
    if (!behaviorAvailable) return new Decision(Path.FALLBACK, "behavior-unavailable");
    if (!snapshotAvailable) return new Decision(Path.FALLBACK, "snapshot-unavailable");
    if (profile == null || profile.status() != UserTasteProfile.Status.READY) {
      return new Decision(Path.FALLBACK, "cold-start");
    }
    if (profile.coverage() < properties.minCoverage()) return new Decision(Path.FALLBACK, "coverage-low");
    if (profile.confidence() < properties.minConfidence()) return new Decision(Path.FALLBACK, "confidence-low");
    if (!inRollout(subject)) return new Decision(Path.FALLBACK, "rollout-excluded");
    return new Decision(Path.PERSONALIZED, "ready");
  }

  private boolean inRollout(String subject) {
    if (properties.rolloutPercentage() >= 100) return true;
    if (properties.rolloutPercentage() <= 0 || subject == null || subject.isBlank()) return false;
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(subject.getBytes(StandardCharsets.UTF_8));
      int bucket = ((digest[0] & 0xff) << 8 | (digest[1] & 0xff)) % 10000;
      return bucket < properties.rolloutPercentage() * 100;
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required for rollout bucketing", exception);
    }
  }

  public Mode mode() { return properties.mode(); }

  public enum Mode { OFF, SHADOW, ON }
  public enum Path { FALLBACK, SHADOW, PERSONALIZED }
  public record Decision(Path path, String reason) { }
  public record Properties(Mode mode, double minCoverage, double minConfidence, double rolloutPercentage) {
    public Properties(Mode mode, double minCoverage, double minConfidence) {
      this(mode, minCoverage, minConfidence, 100);
    }

    public Properties {
      mode = mode == null ? Mode.OFF : mode;
      if (minCoverage < 0 || minCoverage > 1 || minConfidence < 0 || minConfidence > 1) {
        throw new IllegalArgumentException("gate thresholds must be between 0 and 1");
      }
      if (rolloutPercentage < 0 || rolloutPercentage > 100) {
        throw new IllegalArgumentException("rollout percentage must be between 0 and 100");
      }
    }
  }
}
