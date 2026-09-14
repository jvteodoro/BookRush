package com.bookrush.analytics.features;

import java.util.List;

/** Batch embedding SPI. Production implementations must receive a verified local artifact. */
public interface EmbeddingProvider {
  BatchResult embed(List<String> texts);
  record BatchResult(boolean supported, int dimension, List<float[]> vectors, String modelCode, String modelVersion) {
    public static BatchResult unsupported(String model) { return new BatchResult(false, 0, List.of(), model, null); }
  }
}
