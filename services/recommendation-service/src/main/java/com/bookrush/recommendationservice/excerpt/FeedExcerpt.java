package com.bookrush.recommendationservice.excerpt;

import java.util.Map;
import java.util.UUID;

/** Safe, lineage-preserving excerpt attached to a recommendation item. */
public record FeedExcerpt(
    UUID id,
    String text,
    UUID sourceAssetVersionId,
    String textSha256,
    int startCodepoint,
    int endCodepoint,
    String generationMethod,
    String generatorVersion,
    Rank rank) {
  public record Rank(String version, Double score) {}

  public Map<String, Object> asMap() {
    var result = new java.util.LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("text", text);
    result.put("sourceAssetVersionId", sourceAssetVersionId);
    result.put("textSha256", textSha256);
    result.put("startCodepoint", startCodepoint);
    result.put("endCodepoint", endCodepoint);
    result.put("generationMethod", generationMethod);
    result.put("generatorVersion", generatorVersion);
    if (rank != null) result.put("rank", rank);
    return result;
  }
}
