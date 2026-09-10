package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.catalog.CanonicalCatalogPort;
import com.bookrush.ingestion.catalog.CatalogAssetClient;
import com.bookrush.ingestion.catalog.ProcessingLineageClient;
import com.bookrush.ingestion.source.GutenbergRdfParser;
import com.bookrush.ingestion.source.GutenbergProperties;
import com.bookrush.ingestion.source.SnapshotDownloader;
import com.bookrush.ingestion.processing.EpubTextExtractor;
import com.bookrush.ingestion.processing.TextProcessingService;
import com.bookrush.ingestion.storage.RawObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Executes one durable Gutenberg task; all external effects are keyed by its operation key. */
@Service
public class GutenbergImportService {
  private final JdbcTemplate jdbc;
  private final SnapshotDownloader downloader;
  private final RawObjectStore raw;
  private final CanonicalCatalogPort catalog;
  private final ObjectMapper mapper;
  private final CatalogAssetClient assets;
  private final TextProcessingService processing;
  private final ProcessingLineageClient lineage;
  private final GutenbergProperties gutenberg;
  private final Path staging = Path.of(System.getProperty("java.io.tmpdir"), "bookrush-ingestion");
  private final GutenbergRdfParser parser = new GutenbergRdfParser();

  public GutenbergImportService(JdbcTemplate jdbc, SnapshotDownloader downloader, RawObjectStore raw, CanonicalCatalogPort catalog, ObjectMapper mapper, CatalogAssetClient assets, TextProcessingService processing, ProcessingLineageClient lineage, GutenbergProperties gutenberg) {
    this.jdbc = jdbc; this.downloader = downloader; this.raw = raw; this.catalog = catalog; this.mapper = mapper; this.assets = assets; this.processing = processing; this.lineage = lineage; this.gutenberg = gutenberg;
  }

  public void process(LeaseCoordinator.Lease lease) throws Exception {
    var task = jdbc.queryForMap("SELECT t.operation_key, t.ingestion_item_id, i.external_identifier, i.ingestion_job_id, j.parameters::text AS parameters FROM catalog.ingestion_task t JOIN catalog.ingestion_item i ON i.id=t.ingestion_item_id JOIN catalog.ingestion_job j ON j.id=i.ingestion_job_id WHERE t.id=?", lease.taskId());
    var id = String.valueOf(task.get("external_identifier"));
    var rdfUri = URI.create(gutenberg.baseUrl() + "/cache/epub/" + id + "/pg" + id + ".rdf");
    Files.createDirectories(staging);
    var target = staging.resolve("gutenberg-" + id + ".rdf");
    var snapshot = downloader.download(rdfUri, target, "gutenberg/" + id + "/" + id + ".rdf", null, raw);
    var records = parser.parse(Files.newInputStream(snapshot.localFile()), 1);
    if (records.isEmpty()) throw new IllegalStateException("Gutenberg RDF did not contain item " + id);
    var record = records.getFirst();
    var requestHash = sha256(record.externalId() + "\u0000" + record.title() + "\u0000" + record.creator() + "\u0000" + record.language());
    var result = catalog.apply(new CanonicalCatalogPort.CanonicalCatalogCommand(
        String.valueOf(task.get("operation_key")), requestHash, lease.fenceToken(), "GUTENBERG", "GUTENBERG_ID", id,
        record.title() == null ? "Gutenberg " + id : record.title(), record.language(), record.rights(),
        Map.of("rdfUrl", rdfUri.toString(), "snapshotSha256", snapshot.sha256()), null, null, record.creator(), "AUTHOR"));
    var sourceId = jdbc.queryForObject("SELECT id FROM catalog.source WHERE code='GUTENBERG'", UUID.class);
    var sourceRecordId = UUID.randomUUID();
    var metadata = mapper.createObjectNode().put("title", record.title()).put("language", record.language()).put("creator", record.creator()).put("rights", record.rights()).put("rdfUrl", rdfUri.toString());
    jdbc.update("INSERT INTO catalog.source_record(id, source_id, external_id, raw_metadata, retrieved_at, content_hash, raw_sha256, semantic_sha256, raw_locator, small_metadata) VALUES (?, ?, ?, ?::jsonb, clock_timestamp(), ?, ?, ?, ?::jsonb, ?::jsonb) ON CONFLICT DO NOTHING", sourceRecordId, sourceId, id, metadata.toString(), snapshot.sha256(), snapshot.sha256(), requestHash, mapper.createObjectNode().put("bucket", snapshot.objectKey()).toString(), "{}");
    var bookId = UUID.fromString(String.valueOf(result.get("bookId")));
    var editionId = UUID.fromString(String.valueOf(result.get("editionId")));
    boolean processAssets = mapper.readTree(String.valueOf(task.get("parameters"))).path("processAssets").asBoolean(false);
    if (processAssets && record.epubUrl() != null && !record.epubUrl().isBlank()) {
      var epub = staging.resolve("gutenberg-" + id + ".epub");
      var parsedEpub = URI.create(record.epubUrl());
      var configuredHost = URI.create(gutenberg.baseUrl()).getHost();
      var epubUri = !"www.gutenberg.org".equalsIgnoreCase(configuredHost) && !"gutenberg.org".equalsIgnoreCase(configuredHost)
          ? URI.create(gutenberg.baseUrl() + parsedEpub.getPath()) : parsedEpub;
      var epubSnapshot = downloader.download(epubUri, epub, "gutenberg/" + id + "/source.epub", null, raw);
      var sourceResult = assets.uploadSource(bookId, editionId, sourceId, epubSnapshot.localFile(), "gutenberg-" + id + ".epub");
      UUID sourceVersionId = sourceResult.get("version") instanceof Map<?, ?> version && version.get("id") != null ? UUID.fromString(String.valueOf(version.get("id"))) : UUID.randomUUID();
      var normalized = processing.process(editionId, UUID.randomUUID(), new EpubTextExtractor().extract(epub), null);
      var normalizedFile = staging.resolve("gutenberg-" + id + ".normalized.txt");
      var chaptersFile = staging.resolve("gutenberg-" + id + ".chapters.json");
      Files.write(normalizedFile, normalized.normalizedTxt()); Files.write(chaptersFile, normalized.chaptersJson());
      var textResult = assets.uploadDerived(bookId, editionId, sourceId, normalizedFile, "normalized.txt", "TXT");
      var textVersionId = textResult.get("version") instanceof Map<?, ?> version && version.get("id") != null ? UUID.fromString(String.valueOf(version.get("id"))) : UUID.randomUUID();
      var finalProcessing = processing.process(editionId, textVersionId, new String(normalized.normalizedTxt(), java.nio.charset.StandardCharsets.UTF_8), null);
      Files.write(chaptersFile, finalProcessing.chaptersJson());
      var chaptersResult = assets.uploadDerived(bookId, editionId, sourceId, chaptersFile, "chapters.json", "JSON");
      var chapterVersionId = chaptersResult.get("version") instanceof Map<?, ?> version && version.get("id") != null ? UUID.fromString(String.valueOf(version.get("id"))) : UUID.randomUUID();
      lineage.projectChapters(bookId, editionId, textVersionId, finalProcessing.chaptersJson());
      lineage.record(sourceVersionId, java.util.List.of(textVersionId, chapterVersionId), "EPUB_TO_TEXT_AND_CHAPTERS", Map.of("sha256", normalized.sha256()));
    }
    jdbc.update("UPDATE catalog.ingestion_item SET source_record_id=(SELECT id FROM catalog.source_record WHERE source_id=? AND external_id=? ORDER BY created_at DESC LIMIT 1), book_id=?, edition_id=?, status='SUCCEEDED', started_at=COALESCE(started_at, clock_timestamp()), finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=?", sourceId, id, bookId, editionId, task.get("ingestion_item_id"));
  }
  private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
