package com.bookrush.recommendationservice.fatigue;

import java.time.Duration;
import java.time.Instant;

/** Bounded, explainable penalty for recent viewable feed exposures. */
public final class ExposureFatigue {
  private final Config config;

  public ExposureFatigue(Config config) {
    this.config = config;
  }

  public double penalty(Signal signal, Instant now) {
    if (signal == null || signal.viewableCount() <= 0 || signal.lastViewableAt() == null) return 0;
    long age = Math.max(0, Duration.between(signal.lastViewableAt(), now).getSeconds());
    double decay = Math.pow(0.5, (double) age / config.halfLife().toSeconds());
    return Math.min(config.maxPenalty(), config.perExposurePenalty() * signal.viewableCount()) * decay;
  }

  public double multiplier(Signal signal, Instant now) {
    return Math.max(0, 1 - penalty(signal, now));
  }

  public record Signal(long viewableCount, Instant lastViewableAt) { }

  public record Config(String version, Duration halfLife, double perExposurePenalty,
      double maxPenalty) {
    public Config {
      if (version == null || version.isBlank() || halfLife == null || halfLife.isZero()
          || halfLife.isNegative() || perExposurePenalty < 0 || maxPenalty < 0 || maxPenalty > 1) {
        throw new IllegalArgumentException("invalid exposure fatigue configuration");
      }
    }
  }
}
