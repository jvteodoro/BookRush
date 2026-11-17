package com.bookrush.ingestion.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookrush.ingestion.catalog.*;
import com.bookrush.ingestion.observability.IngestionMetrics;
import com.bookrush.ingestion.processing.TextProcessingService;
import com.bookrush.ingestion.source.GutenbergProperties;
import com.bookrush.ingestion.source.SnapshotDownloader;
import com.bookrush.ingestion.storage.RawObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class GutenbergImportServiceTest {
  @TempDir Path temp;

  @Test
  void importsEpubWithStandardNcxDoctypeThroughTextChaptersCoverAndLineage() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var downloader = mock(SnapshotDownloader.class);
    var raw = mock(RawObjectStore.class);
    var catalog = mock(CanonicalCatalogPort.class);
    var assets = mock(CatalogAssetClient.class);
    var lineage = mock(ProcessingLineageClient.class);
    var subjects = mock(SubjectCatalogClient.class);
    var metrics = mock(IngestionMetrics.class);
    var mapper = new ObjectMapper();
    var lease = new LeaseCoordinator.Lease(UUID.randomUUID(), 1, Instant.now().plusSeconds(120));
    UUID item = UUID.randomUUID(), book = UUID.randomUUID(), edition = UUID.randomUUID();
    UUID source = UUID.randomUUID(), record = UUID.randomUUID(), cover = UUID.randomUUID();
    UUID epubVersion = UUID.randomUUID(), textVersion = UUID.randomUUID(), chaptersVersion = UUID.randomUUID();
    when(jdbc.queryForMap(anyString(), eq(lease.taskId()))).thenReturn(Map.of(
        "operation_key", "ncx-import", "ingestion_item_id", item,
        "ingestion_job_id", UUID.randomUUID(), "external_identifier", "1342",
        "parameters", "{\"processAssets\":true}"));
    when(jdbc.queryForObject(anyString(), eq(UUID.class))).thenReturn(source);
    when(jdbc.queryForObject(anyString(), eq(UUID.class), any(), any())).thenReturn(record);
    when(catalog.apply(any())).thenReturn(Map.of("bookId", book, "editionId", edition));

    Path rdf = temp.resolve("book.rdf");
    try (var input = getClass().getResourceAsStream("/fixtures/gutenberg/catalog.rdf")) {
      Files.copy(input, rdf);
    }
    Path epub = temp.resolve("book.epub");
    try (var zip = new ZipOutputStream(Files.newOutputStream(epub))) {
      put(zip, "META-INF/container.xml", """
          <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
          <rootfile full-path="content.opf"/></rootfiles></container>
          """);
      put(zip, "content.opf", """
          <package xmlns="http://www.idpf.org/2007/opf"><manifest>
          <item id="text" href="text.xhtml" media-type="application/xhtml+xml"/>
          <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
          </manifest><spine toc="ncx"><itemref idref="text"/></spine></package>
          """);
      put(zip, "toc.ncx", """
          <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://127.0.0.1:1/ncx.dtd">
          <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint>
          <navLabel><text>Chapter One</text></navLabel><content src="text.xhtml"/>
          </navPoint></navMap></ncx>
          """);
      put(zip, "text.xhtml", "<html><body><h1>Chapter One</h1><p>Book text.</p></body></html>");
    }
    when(downloader.download(any(), any(), anyString(), isNull(), eq(raw)))
        .thenAnswer(invocation -> {
          Path target = invocation.getArgument(1);
          String key = invocation.getArgument(2);
          Path fixture = key.endsWith(".rdf") ? rdf : epub;
          Files.copy(fixture, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          return new SnapshotDownloader.SnapshotResult(invocation.getArgument(0), target,
              key, Files.size(target), "a".repeat(64), null);
        });
    when(assets.uploadSource(eq(book), eq(edition), eq(source), any(), anyString()))
        .thenAnswer(invocation -> Map.of("version", Map.of("id", epubVersion)));
    when(assets.uploadDerived(eq(book), eq(edition), eq(source), any(), eq("normalized.txt"), eq("TXT")))
        .thenAnswer(invocation -> {
          assertTrue(Files.readString(invocation.getArgument(3)).contains("Book text."));
          return Map.of("version", Map.of("id", textVersion));
        });
    when(assets.uploadDerived(eq(book), eq(edition), eq(source), any(), eq("chapters.json"), eq("JSON")))
        .thenAnswer(invocation -> {
          var json = mapper.readTree(Files.readAllBytes(invocation.getArgument(3)));
          assertEquals(textVersion.toString(), json.path("textAssetVersionId").asText());
          assertEquals("Chapter One", json.path("chapters").get(0).path("title").asText());
          return Map.of("version", Map.of("id", chaptersVersion));
        });
    when(assets.uploadThumbnail(eq(book), eq(edition), eq(source), any(), anyString()))
        .thenAnswer(invocation -> Map.of("asset", Map.of("id", cover)));
    var service = new GutenbergImportService(jdbc, downloader, raw, catalog, mapper, assets,
        new TextProcessingService(mapper), lineage, subjects,
        new GutenbergProperties("https://example.test", false),
        mock(PublisherSubmissionLinkClient.class), metrics);

    service.process(lease);

    verify(subjects).assign(book, "GUTENBERG", "Love stories", "GUTENBERG", record, null, "SOURCE");
    verify(assets).approve(book, cover);
    verify(lineage).projectChapters(eq(book), eq(edition), eq(textVersion), any());
    verify(lineage).record(eq(epubVersion), eq(java.util.List.of(textVersion, chaptersVersion)),
        eq("EPUB_TO_TEXT_AND_CHAPTERS"), anyMap());
    verify(jdbc).update(contains("status='SUCCEEDED'"), eq(source), eq("1342"), eq(book), eq(edition), eq(item));
    verify(metrics).acquired();
    verify(metrics).normalized();
  }

  private static void put(ZipOutputStream zip, String name, String value) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
