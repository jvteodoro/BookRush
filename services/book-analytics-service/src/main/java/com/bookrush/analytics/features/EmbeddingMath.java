package com.bookrush.analytics.features;

import java.util.*;

/**
 * Pure vector operations used by BGE/prototype adapters; vectors are always L2-normalized at
 * boundaries.
 */
public final class EmbeddingMath {
  private EmbeddingMath() {}

  public static float[] l2Normalize(float[] input) {
    Objects.requireNonNull(input);
    double norm = 0;
    for (float x : input) norm += (double) x * x;
    if (norm == 0) throw new IllegalArgumentException("zero vector");
    float[] out = input.clone();
    float n = (float) Math.sqrt(norm);
    for (int i = 0; i < out.length; i++) out[i] /= n;
    return out;
  }

  public static float[] weightedMean(List<float[]> vectors, List<Integer> weights) {
    if (vectors.isEmpty() || vectors.size() != weights.size())
      throw new IllegalArgumentException("vectors and weights required");
    int d = vectors.getFirst().length;
    float[] out = new float[d];
    long total = 0;
    for (int j = 0; j < vectors.size(); j++) {
      if (vectors.get(j).length != d || weights.get(j) <= 0)
        throw new IllegalArgumentException("invalid vector or weight");
      total += weights.get(j);
      for (int i = 0; i < d; i++) out[i] += vectors.get(j)[i] * weights.get(j);
    }
    for (int i = 0; i < d; i++) out[i] /= total;
    return l2Normalize(out);
  }

  public static double cosine(float[] a, float[] b) {
    if (a.length != b.length) throw new IllegalArgumentException("dimension mismatch");
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  /** Returns a raw cosine score in [-1, 1]; it is never converted to a probability. */
  public static double cosine(List<Double> a, List<Double> b) {
    if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
      return 0d;
    }
    double dot = 0d;
    double normA = 0d;
    double normB = 0d;
    for (int i = 0; i < a.size(); i++) {
      double left = a.get(i);
      double right = b.get(i);
      dot += left * right;
      normA += left * left;
      normB += right * right;
    }
    if (normA == 0d || normB == 0d) return 0d;
    return Math.max(-1d, Math.min(1d, dot / (Math.sqrt(normA) * Math.sqrt(normB))));
  }
}
