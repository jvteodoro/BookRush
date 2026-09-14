package com.bookrush.ingestion.staging;

import com.bookrush.ingestion.source.OpenLibraryDumpParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;

/** Converts one immutable Open Library dump snapshot into a sealed DuckDB generation. */
public final class OpenLibraryStageJob {
  private final ObjectMapper mapper;
  private final OpenLibraryDumpParser parser;

  public OpenLibraryStageJob(ObjectMapper mapper, int maxLineBytes) {
    this.mapper = mapper;
    this.parser = new OpenLibraryDumpParser(mapper, maxLineBytes);
  }

  public DuckDbStagingWriter.Manifest stage(Path snapshot, Path database, String sourceId,
      String parserVersion, Map<String, String> filters, int maxRecords) throws Exception {
    if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId is required");
    if (maxRecords <= 0) throw new IllegalArgumentException("maxRecords must be positive");
    var effectiveFilters = new LinkedHashMap<String, String>();
    if (filters != null) effectiveFilters.putAll(filters);
    effectiveFilters.put("source_id", sourceId);
    effectiveFilters.put("max_records", Integer.toString(maxRecords));
    effectiveFilters.put("snapshot_sha256", sha256(snapshot));
    var manifestPath = database.toAbsolutePath().normalize().resolveSibling(database.getFileName() + ".manifest.json");
    if (Files.exists(manifestPath) && Files.exists(database)) {
      var existing = mapper.readValue(manifestPath.toFile(), DuckDbStagingWriter.Manifest.class);
      if ("READY".equals(existing.state()) && existing.filters().equals(effectiveFilters)
          && existing.parserVersion().equals(parserVersion)) return existing;
    }
    try (var writer = new DuckDbStagingWriter(database, mapper, parserVersion, effectiveFilters);
         var input = Files.newInputStream(snapshot);
         var reader = new BufferedReader(new InputStreamReader(isGzip(snapshot, input), StandardCharsets.UTF_8))) {
      for (var row : parser.parse(reader, maxRecords)) {
        var table = tableFor(row);
        writer.append(table, new DuckDbStagingWriter.StagingRecord(sourceId, row.key(), row.revision(),
            snapshot.toAbsolutePath().normalize() + "#" + row.sourceLine(), sha256(row.payload().toString()),
            Long.toString(row.sourceLine()), row.payload().toString()));
      }
      return writer.sealReady();
    }
  }

  private static InputStream isGzip(Path snapshot, InputStream input) throws Exception {
    return snapshot.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(input) : input;
  }

  private static String tableFor(OpenLibraryDumpParser.DumpRecord row) {
    if (row.isDelete()) return "stg_rejected_record";
    if (row.namespace().startsWith("/authors/")) return "stg_author";
    if (row.namespace().startsWith("/works/")) return "stg_work";
    if (row.namespace().startsWith("/books/")) return "stg_edition";
    if (row.namespace().startsWith("/redirects/")) return "stg_redirect";
    return "stg_rejected_record";
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String sha256(Path file) throws Exception {
    try (var input = Files.newInputStream(file)) {
      var digest = MessageDigest.getInstance("SHA-256");
      var buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
      return HexFormat.of().formatHex(digest.digest());
    }
  }
}
