package com.bookrush.analytics.features;
import java.util.List;
public final class DisabledEmbeddingProvider implements EmbeddingProvider {
  @Override public BatchResult embed(List<String> texts) { return BatchResult.unsupported("local-model-disabled"); }
}
