package com.bookrush.ingestion.jobs;

import com.bookrush.platform.core.Sha256;
import com.bookrush.ingestion.processing.PlainTextNormalizer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class AssetNormalizeJob {
  private final PlainTextNormalizer normalizer = new PlainTextNormalizer();
  public Normalized normalize(String input) {
    String text = normalizer.normalize(input);
    return new Normalized(text, sha256(text));
  }
  private String sha256(String value) {
    try {
      return Sha256.digest(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), Long.MAX_VALUE);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("normalized text hash failed", e);
    }
  }
  public record Normalized(String text, String sha256) {}
}
