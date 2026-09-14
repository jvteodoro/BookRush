package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.ingestion.staging.OpenLibraryStageJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class OpenLibraryStageJobTest {
  @Test
  void stagesNamespacesDeletesAndPublishesReadyManifest() throws Exception {
    var root = Files.createTempDirectory("bookrush-openlibrary-stage-");
    var snapshot = root.resolve("snapshot.tsv.gz");
    try (var gzip = new GZIPOutputStream(Files.newOutputStream(snapshot))) {
      gzip.write(("/authors/OL1A\t1\t2024\tignored\t{\"type\":{\"key\":\"/type/author\"},\"name\":\"Jane\"}\n"
          + "/works/OL1W\t2\t2024\tignored\t{\"type\":{\"key\":\"/type/work\"},\"title\":\"Pride\"}\n"
          + "/books/OL1M\t3\t2024\tignored\t{\"type\":{\"key\":\"/type/edition\"},\"title\":\"Pride\"}\n"
          + "/works/OL9W\t4\t2024\tignored\t{\"type\":{\"key\":\"/type/delete\"}}\n").getBytes());
    }
    var database = root.resolve("staging.duckdb");
    var manifest = new OpenLibraryStageJob(new ObjectMapper(), 4096)
        .stage(snapshot, database, "OPEN_LIBRARY", "parser-test-2", Map.of("edition", "works"), 100);

    assertEquals("READY", manifest.state());
    assertEquals(4, manifest.recordCount());
    assertEquals("works", manifest.filters().get("edition"));
    assertEquals(64, manifest.filters().get("snapshot_sha256").length());
    assertEquals(1L, manifest.tableCounts().get("stg_author"));
    assertEquals(1L, manifest.tableCounts().get("stg_work"));
    assertEquals(1L, manifest.tableCounts().get("stg_edition"));
    assertEquals(1L, manifest.tableCounts().get("stg_rejected_record"));
    try (var connection = DriverManager.getConnection("jdbc:duckdb:" + database);
         var statement = connection.createStatement();
         var rows = statement.executeQuery("select count(*) from stg_rejected_record where raw_payload like '%delete%'") ) {
      rows.next();
      assertEquals(1, rows.getInt(1));
    }
    var replay = new OpenLibraryStageJob(new ObjectMapper(), 4096)
        .stage(snapshot, database, "OPEN_LIBRARY", "parser-test-2", Map.of("edition", "works"), 100);
    assertEquals(manifest, replay);
  }
}
