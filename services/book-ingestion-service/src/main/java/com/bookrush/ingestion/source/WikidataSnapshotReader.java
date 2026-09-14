package com.bookrush.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Reads a bounded, checksum-verified Wikidata JSONL export without network access. */
public final class WikidataSnapshotReader {
  private final ObjectMapper mapper;
  public WikidataSnapshotReader(ObjectMapper mapper) { this.mapper = mapper; }

  public Snapshot read(Path file, String expectedSha256, int maxRecords, long maxBytes) throws IOException {
    var size = Files.size(file);
    if (size > maxBytes) throw new IllegalArgumentException("Wikidata snapshot exceeds configured limit");
    var bytes = Files.readAllBytes(file);
    var actual = HexFormat.of().formatHex(MessageDigest.getInstanceUnchecked("SHA-256", bytes));
    if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(actual)) throw new IllegalArgumentException("Wikidata snapshot checksum mismatch");
    var records = new ArrayList<Record>();
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      long lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) continue;
        if (records.size() >= maxRecords) throw new IllegalArgumentException("Wikidata snapshot record limit exceeded");
        var node = mapper.readTree(line);
        var qid = node.path("qid").asText(null);
        if (qid == null || !qid.matches("Q[1-9][0-9]*")) throw new IllegalArgumentException("invalid Wikidata QID at line " + lineNumber);
        records.add(new Record(qid, node.path("claims"), lineNumber));
      }
    }
    return new Snapshot(file, actual, List.copyOf(records));
  }

  public record Snapshot(Path file, String sha256, List<Record> records) {}
  public record Record(String qid, JsonNode claims, long sourceLine) {}

  private static final class MessageDigest {
    static byte[] getInstanceUnchecked(String algorithm, byte[] input) {
      try { return java.security.MessageDigest.getInstance(algorithm).digest(input); }
      catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
  }
}
