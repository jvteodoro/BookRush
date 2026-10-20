package com.bookrush.recommendationservice.ranking;

import com.bookrush.recommendationservice.taste.UserTasteProfile;

/** Decides whether personalized serving is safe without making it a hard dependency. */
public final class PersonalizedFeedGate {
  private final Properties properties;
  public PersonalizedFeedGate(Properties properties) { this.properties = properties; }

  public Decision decide(UserTasteProfile profile, boolean behaviorAvailable, boolean snapshotAvailable) {
    if (properties.mode() == Mode.OFF) return new Decision(Path.FALLBACK, "disabled");
    if (properties.mode() == Mode.SHADOW) return new Decision(Path.SHADOW, "shadow");
    if (!behaviorAvailable) return new Decision(Path.FALLBACK, "behavior-unavailable");
    if (!snapshotAvailable) return new Decision(Path.FALLBACK, "snapshot-unavailable");
    if (profile == null || profile.status() != UserTasteProfile.Status.READY) {
      return new Decision(Path.FALLBACK, "profile-missing");
    }
    if (profile.coverage() < properties.minCoverage()) return new Decision(Path.FALLBACK, "coverage-low");
    if (profile.confidence() < properties.minConfidence()) return new Decision(Path.FALLBACK, "confidence-low");
    return new Decision(Path.PERSONALIZED, "ready");
  }

  public Mode mode() { return properties.mode(); }

  public enum Mode { OFF, SHADOW, ON }
  public enum Path { FALLBACK, SHADOW, PERSONALIZED }
  public record Decision(Path path, String reason) { }
  public record Properties(Mode mode, double minCoverage, double minConfidence) {
    public Properties {
      mode = mode == null ? Mode.OFF : mode;
      if (minCoverage < 0 || minCoverage > 1 || minConfidence < 0 || minConfidence > 1) {
        throw new IllegalArgumentException("gate thresholds must be between 0 and 1");
      }
    }
  }
}
