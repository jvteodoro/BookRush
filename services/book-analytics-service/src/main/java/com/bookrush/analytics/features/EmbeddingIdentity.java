package com.bookrush.analytics.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Idempotency identity for local embeddings; does not load or download a model. */
public record EmbeddingIdentity(String modelCode, String modelVersion, int dimension, String inputSha256) {
  public EmbeddingIdentity {
    if (modelCode == null || modelCode.isBlank() || modelVersion == null || dimension <= 0 || inputSha256 == null || !inputSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid embedding identity");
  }
  public static String inputHash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
