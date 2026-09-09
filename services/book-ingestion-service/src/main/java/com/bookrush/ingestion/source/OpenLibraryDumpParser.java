package com.bookrush.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Parses the first four TSV separators and treats the fifth field as JSON. */
public final class OpenLibraryDumpParser {
  private final ObjectMapper mapper;
  private final int maxLineBytes;

  public OpenLibraryDumpParser(ObjectMapper mapper, int maxLineBytes) {
    this.mapper = mapper;
    this.maxLineBytes = maxLineBytes;
  }

  public List<DumpRecord> parseGzip(InputStream compressed, int maxRecords) throws Exception {
    try (var gzip = new GZIPInputStream(compressed);
         var reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
      return parse(reader, maxRecords);
    }
  }

  public List<DumpRecord> parse(BufferedReader reader, int maxRecords) throws Exception {
    var result = new ArrayList<DumpRecord>();
    String line;
    long lineNumber = 0;
    while (result.size() < maxRecords && (line = reader.readLine()) != null) {
      lineNumber++;
      if (line.getBytes(StandardCharsets.UTF_8).length > maxLineBytes) throw new IllegalArgumentException("dump record too large at line " + lineNumber);
      var fields = line.split("\\t", 5);
      if (fields.length != 5) throw new IllegalArgumentException("dump record must contain five fields at line " + lineNumber);
      JsonNode payload = mapper.readTree(fields[4]);
      result.add(new DumpRecord(fields[0], fields[1], fields[2], fields[3], payload, lineNumber));
    }
    return List.copyOf(result);
  }

  public record DumpRecord(String namespace, String key, String revision, String lastModified,
      JsonNode payload, long sourceLine) {
    public boolean isDelete() { return payload.path("type").path("key").asText().equals("/type/delete"); }
  }
}
