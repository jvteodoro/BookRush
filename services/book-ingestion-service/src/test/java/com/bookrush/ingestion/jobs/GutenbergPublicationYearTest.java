package com.bookrush.ingestion.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookrush.ingestion.catalog.*;
import com.bookrush.ingestion.processing.TextProcessingService;
import com.bookrush.ingestion.source.GutenbergProperties;
import com.bookrush.ingestion.source.SnapshotDownloader;
import com.bookrush.ingestion.storage.RawObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class GutenbergPublicationYearTest {
  @TempDir Path temp;

  @Test
  void metadataOnlyReimportForwardsBibliographicYearAndKeepsDigitalDateSeparate() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var downloader = mock(SnapshotDownloader.class);
    var catalog = mock(CanonicalCatalogPort.class);
    var raw = mock(RawObjectStore.class);
    var lease = new LeaseCoordinator.Lease(UUID.randomUUID(), 1, Instant.now().plusSeconds(60));
    when(jdbc.queryForMap(anyString(), eq(lease.taskId()))).thenReturn(Map.of(
        "operation_key", "year-reprocess", "ingestion_item_id", UUID.randomUUID(),
        "ingestion_job_id", UUID.randomUUID(), "external_identifier", "1342",
        "parameters", "{\"processAssets\":false}"));
    when(jdbc.queryForObject(anyString(), eq(UUID.class))).thenReturn(UUID.randomUUID());
    when(jdbc.queryForObject(anyString(), eq(UUID.class), any(), any())).thenReturn(UUID.randomUUID());
    when(catalog.apply(any())).thenReturn(Map.of("bookId", UUID.randomUUID(), "editionId", UUID.randomUUID()));
    Path rdf = temp.resolve("book.rdf");
    Files.writeString(rdf, """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
          xmlns:dcterms="http://purl.org/dc/terms/" xmlns:pgterms="http://www.gutenberg.org/2009/pgterms/">
          <pgterms:ebook rdf:about="ebooks/1342"><dcterms:title>Fixture</dcterms:title>
            <dcterms:issued>2008-08-26</dcterms:issued>
            <pgterms:marc260>London: Publisher, 1899.</pgterms:marc260>
          </pgterms:ebook>
        </rdf:RDF>
        """);
    when(downloader.download(any(), any(), anyString(), isNull(), eq(raw))).thenReturn(
        new SnapshotDownloader.SnapshotResult(URI.create("https://example.test/book.rdf"), rdf,
            "book.rdf", Files.size(rdf), "a".repeat(64), null));
    var service = new GutenbergImportService(jdbc, downloader, raw, catalog, new ObjectMapper(),
        mock(CatalogAssetClient.class), mock(TextProcessingService.class), mock(ProcessingLineageClient.class),
        mock(SubjectCatalogClient.class), new GutenbergProperties("https://example.test", false),
        mock(PublisherSubmissionLinkClient.class));

    service.process(lease);

    var command = ArgumentCaptor.forClass(CanonicalCatalogPort.CanonicalCatalogCommand.class);
    verify(catalog).apply(command.capture());
    assertEquals(1899, command.getValue().publicationYear());
    assertEquals(1899, command.getValue().metadata().get("publicationYear"));
    String firstHash = command.getValue().requestHash();
    Files.writeString(rdf, Files.readString(rdf).replace("1899", "1900"));
    service.process(lease);
    verify(catalog, times(2)).apply(command.capture());
    assertEquals(1900, command.getValue().publicationYear());
    assertNotEquals(firstHash, command.getValue().requestHash());
  }
}
