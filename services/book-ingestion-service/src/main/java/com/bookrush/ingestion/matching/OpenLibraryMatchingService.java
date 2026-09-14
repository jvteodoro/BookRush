package com.bookrush.ingestion.matching;

import com.bookrush.ingestion.source.OpenLibraryDumpParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Applies the deterministic matcher to staged Open Library records and keeps audit evidence. */
public final class OpenLibraryMatchingService {
  private final MatchingEngine engine;

  public OpenLibraryMatchingService() {
    this(new MatchingEngine());
  }

  public OpenLibraryMatchingService(MatchingEngine engine) {
    this.engine = engine;
  }

  public AuditedDecision decide(OpenLibraryDumpParser.DumpRecord record,
      MatchingEngine.Input input, List<MatchingEngine.Candidate> candidates) {
    if (record == null || input == null) throw new IllegalArgumentException("record and input are required");
    if (candidates == null) throw new IllegalArgumentException("candidates are required");
    var decision = engine.decide(input, candidates);
    var evidence = record.namespace() + "|" + record.key() + "|" + record.revision() + "|" + record.payload();
    return new AuditedDecision(record.namespace(), record.key(), record.revision(), sha256(evidence), decision);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public record AuditedDecision(String namespace, String externalId, String revision,
      String evidenceSha256, MatchingEngine.Decision decision) {}
}
