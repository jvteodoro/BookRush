package com.bookrush.recommendationservice.taste;

/** Versioned cold-start policy for blending personalized and global ranking. */
public final class ProfileBlendPolicy {
  private final Config config;

  public ProfileBlendPolicy(Config config) {
    this.config = config;
  }

  public Confidence classify(UserTasteProfile profile) {
    if (profile == null || profile.status() == UserTasteProfile.Status.EMPTY
        || profile.effectiveSignalCount() == 0) return new Confidence(Level.EMPTY, 0);
    double value = clamp(profile.confidence());
    Level level = value >= config.establishedThreshold() ? Level.ESTABLISHED
        : value >= config.lowThreshold() ? Level.LOW : Level.EMPTY;
    return new Confidence(level, value);
  }

  public double blend(double globalScore, double personalizedScore, UserTasteProfile profile) {
    if (!Double.isFinite(globalScore) || !Double.isFinite(personalizedScore)) {
      throw new IllegalArgumentException("scores must be finite");
    }
    return globalScore + classify(profile).value() * (personalizedScore - globalScore);
  }

  private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

  public enum Level { EMPTY, LOW, ESTABLISHED }
  public record Confidence(Level level, double value) { }

  public record Config(String version, double lowThreshold, double establishedThreshold) {
    public Config {
      if (version == null || version.isBlank() || lowThreshold < 0 || establishedThreshold > 1
          || lowThreshold >= establishedThreshold) {
        throw new IllegalArgumentException("invalid profile blend configuration");
      }
    }
  }
}
