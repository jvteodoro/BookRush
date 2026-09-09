package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.processing.PlainTextNormalizer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class AssetNormalizeJob {
  private final PlainTextNormalizer normalizer = new PlainTextNormalizer();
  public Normalized normalize(String input) {
    String text = normalizer.normalize(input);
    return new Normalized(text, sha256(text));
  }
  private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  public record Normalized(String text, String sha256) {}
}
