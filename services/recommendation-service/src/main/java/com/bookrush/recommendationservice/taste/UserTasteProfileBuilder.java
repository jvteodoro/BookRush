package com.bookrush.recommendationservice.taste;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic bootstrap profile builder; impressions alone never add affinity. */
public final class UserTasteProfileBuilder {
  private final Config config;

  public UserTasteProfileBuilder(Config config) {
    this.config = config;
  }

  public UserTasteProfile build(List<Signal> signals, Map<UUID, BookEmbedding> embeddings,
      Instant now) {
    var contributions = signals.stream()
        .filter(signal -> signal.bookId() != null && signal.positiveWeight() > 0)
        .filter(signal -> embeddings.containsKey(signal.bookId()))
        .limit(config.maxSignals())
        .toList();
    if (contributions.isEmpty()) {
      return new UserTasteProfile(UserTasteProfile.Status.EMPTY, null, config.version(),
          config.hash(), 0, 0, 0, now);
    }
    int dimension = embeddings.get(contributions.getFirst().bookId()).vector().size();
    double[] sum = new double[dimension];
    double totalWeight = 0;
    int effective = 0;
    for (Signal signal : contributions) {
      BookEmbedding embedding = embeddings.get(signal.bookId());
      if (embedding.vector().size() != dimension || !embedding.valid()) continue;
      double recency = decay(signal.lastEventAt(), now);
      double weight = Math.min(config.maxWeightPerBook(), signal.positiveWeight() * recency);
      if (weight <= 0) continue;
      for (int index = 0; index < dimension; index++) sum[index] += embedding.vector().get(index) * weight;
      totalWeight += weight;
      effective++;
    }
    double norm = 0;
    for (double value : sum) norm += value * value;
    norm = Math.sqrt(norm);
    if (effective == 0 || norm == 0) {
      return new UserTasteProfile(UserTasteProfile.Status.EMPTY, null, config.version(),
          config.hash(), 0, 0, 0, now);
    }
    var vector = new ArrayList<Double>(dimension);
    for (double value : sum) vector.add(value / norm);
    double coverage = (double) effective / Math.max(1, contributions.size());
    double confidence = Math.min(1, coverage * Math.min(1, totalWeight / config.confidenceWeightTarget()));
    return new UserTasteProfile(UserTasteProfile.Status.READY, vector, config.version(),
        config.hash(), effective, coverage, confidence, now);
  }

  private double decay(Instant eventAt, Instant now) {
    if (eventAt == null || !eventAt.isBefore(now)) return 1;
    long ageSeconds = Math.max(0, Duration.between(eventAt, now).getSeconds());
    return Math.pow(0.5, (double) ageSeconds / config.halfLife().toSeconds());
  }

  public record Config(String version, Duration halfLife, int maxSignals,
      double maxWeightPerBook, double confidenceWeightTarget) {
    public Config {
      if (version == null || version.isBlank() || halfLife == null || halfLife.isZero()
          || halfLife.isNegative() || maxSignals < 1 || maxWeightPerBook <= 0
          || confidenceWeightTarget <= 0) {
        throw new IllegalArgumentException("invalid taste profile configuration");
      }
    }

    public String hash() {
      try {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((version + "|" + halfLife + "|" + maxSignals + "|"
                + maxWeightPerBook + "|" + confidenceWeightTarget)
                .getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException("SHA-256 is required for profile configuration", exception);
      }
    }
  }

  public record Signal(UUID bookId, long impressionCount, long openCount,
      long completionCount, long activeSeconds, double maxProgress, boolean liked,
      boolean saved, Instant lastEventAt) {
    public double positiveWeight() {
      double weight = 0;
      if (liked) weight += 3.0;
      if (saved) weight += 2.5;
      weight += Math.min(1, completionCount) * 2.0;
      weight += Math.min(1, openCount) * 0.5;
      weight += Math.min(1, Math.max(0, activeSeconds) / 1800.0) * 1.0;
      weight += Math.min(1, Math.max(0, maxProgress) / 100.0) * 1.5;
      return weight;
    }
  }

  public record BookEmbedding(UUID bookId, String model, String modelVersion,
      UUID sourceAssetVersionId, String inputSha256, List<Double> vector) {
    public BookEmbedding {
      vector = vector == null ? List.of() : List.copyOf(vector);
    }

    public boolean valid() {
      return bookId != null && model != null && !model.isBlank()
          && modelVersion != null && !modelVersion.isBlank()
          && sourceAssetVersionId != null && inputSha256 != null && inputSha256.length() == 64
          && !vector.isEmpty() && vector.stream().allMatch(value -> value != null && Double.isFinite(value));
    }
  }
}
