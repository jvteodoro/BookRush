package com.bookrush.analytics.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Builds deterministic, language-scoped add-alpha unigram artifacts without remote I/O. */
public final class CorpusFrequencyBuilder {
  public static final String TOKENIZER_VERSION = "unicode-word-nfc-lower-v1";
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");
  private CorpusFrequencyBuilder() {}

  public static CorpusFrequencyArtifact build(String language, String snapshot, List<String> normalizedTexts, double alpha) {
    if (!List.of("en", "pt").contains(language)) throw new IllegalArgumentException("language must be en or pt");
    if (snapshot == null || snapshot.isBlank() || alpha <= 0) throw new IllegalArgumentException("snapshot and alpha are required");
    var counts = new java.util.TreeMap<String, Long>();
    long total = 0;
    for (String text : normalizedTexts) {
      if (text == null) continue;
      var matcher = TOKEN.matcher(text);
      while (matcher.find()) {
        String token = matcher.group().toLowerCase(Locale.ROOT);
        counts.merge(token, 1L, Long::sum);
        total++;
      }
    }
    if (total == 0) throw new IllegalArgumentException("eligible corpus is empty");
    var immutable = Map.copyOf(counts);
    String canonical = canonical(language, snapshot, alpha, immutable);
    return new CorpusFrequencyArtifact(language, snapshot, TOKENIZER_VERSION, alpha, total, immutable.size(), immutable, sha256(canonical));
  }

  public static CorpusFrequencyModel toModel(CorpusFrequencyArtifact artifact) {
    return new CorpusFrequencyModel(artifact.language(), artifact.corpusSnapshot(), artifact.counts(), artifact.tokenCount(), artifact.smoothingAlpha());
  }

  /** Canonical JSON-like text for object-storage publication and checksum verification. */
  public static String serialize(CorpusFrequencyArtifact artifact) {
    Objects.requireNonNull(artifact);
    var entries = new ArrayList<>(artifact.counts().entrySet());
    entries.sort(Map.Entry.comparingByKey());
    var out = new StringBuilder("{\"language\":\"").append(escape(artifact.language()))
        .append("\",\"corpus_snapshot\":\"").append(escape(artifact.corpusSnapshot()))
        .append("\",\"tokenizer_version\":\"").append(artifact.tokenizerVersion())
        .append("\",\"smoothing_alpha\":").append(artifact.smoothingAlpha())
        .append(",\"token_count\":").append(artifact.tokenCount()).append(",\"counts\":{");
    for (int i=0;i<entries.size();i++) { if (i>0) out.append(','); var e=entries.get(i); out.append('"').append(escape(e.getKey())).append("\":").append(e.getValue()); }
    return out.append("}}\n").toString();
  }
  private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

  private static String canonical(String language, String snapshot, double alpha, Map<String, Long> counts) {
    var entries = new ArrayList<>(counts.entrySet());
    entries.sort(Map.Entry.comparingByKey());
    var value = new StringBuilder(language).append('\n').append(snapshot).append('\n').append(alpha).append('\n');
    for (var entry : entries) value.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
    return value.toString();
  }
  private static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception exception) { throw new IllegalStateException(exception); }
  }
}
