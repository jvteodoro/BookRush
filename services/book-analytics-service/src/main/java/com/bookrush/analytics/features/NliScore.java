package com.bookrush.analytics.features;

/** Raw NLI output and derived literary-model scores. Values are model scores, never probabilities. */
public record NliScore(double entailment, double neutral, double contradiction) {
  public NliScore {
    if (!Double.isFinite(entailment) || !Double.isFinite(neutral) || !Double.isFinite(contradiction)) {
      throw new IllegalArgumentException("NLI scores must be finite");
    }
    if (entailment < 0 || neutral < 0 || contradiction < 0) {
      throw new IllegalArgumentException("NLI scores cannot be negative");
    }
  }

  public double support() {
    double denominator = entailment + contradiction;
    return denominator == 0 ? 0 : entailment / denominator;
  }

  public double confidence() {
    return Math.max(0, Math.min(1, 1 - neutral));
  }
}
