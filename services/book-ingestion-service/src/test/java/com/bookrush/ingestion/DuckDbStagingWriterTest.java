package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookrush.ingestion.staging.DuckDbStagingWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DuckDbStagingWriterTest {
  @Test
  void sealsGenerationWithParserFiltersCountsAndChecksum() throws Exception {
    var root = Files.createTempDirectory("bookrush-staging-");
    var database = root.resolve("openlibrary.duckdb");
    try (var writer = new DuckDbStagingWriter(database, new ObjectMapper(), "parser-test-1", Map.of("language", "eng"))) {
      writer.append("stg_work", new DuckDbStagingWriter.StagingRecord(
          "OPEN_LIBRARY", "OL1W", "7", "raw://works/OL1W", "a".repeat(64), "1", "{\"title\":\"Example\"}"));
      writer.append("stg_work", new DuckDbStagingWriter.StagingRecord(
          "OPEN_LIBRARY", "OL2W", "8", "raw://works/OL2W", "b".repeat(64), "2", "{\"title\":\"Second\"}"));

      var manifest = writer.sealReady();
      assertEquals("READY", manifest.state());
      assertEquals("parser-test-1", manifest.parserVersion());
      assertEquals("eng", manifest.filters().get("language"));
      assertEquals(2L, manifest.recordCount());
      assertEquals(2L, manifest.tableCounts().get("stg_work"));
      assertNotNull(manifest.sha256());
      assertEquals(64, manifest.sha256().length());
      assertEquals(manifest, new ObjectMapper().readValue(
          database.resolveSibling("openlibrary.duckdb.manifest.json").toFile(), DuckDbStagingWriter.Manifest.class));
    }
  }

  @Test
  void rejectsConcurrentWriterForSameGeneration() throws Exception {
    var root = Files.createTempDirectory("bookrush-staging-lock-");
    var database = root.resolve("openlibrary.duckdb");
    try (var ignored = new DuckDbStagingWriter(database, new ObjectMapper())) {
      assertThrows(java.nio.channels.OverlappingFileLockException.class,
          () -> new DuckDbStagingWriter(database, new ObjectMapper()));
    }
  }
}
