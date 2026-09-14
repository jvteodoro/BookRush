package com.bookrush.ingestion.staging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** One-writer DuckDB generation; readers consume only a sealed READY manifest. */
public final class DuckDbStagingWriter implements AutoCloseable {
  private static final List<String> TABLES = List.of("stg_author", "stg_work", "stg_edition", "stg_identifier", "stg_redirect", "stg_rejected_record");
  private final Path database;
  private final Path manifest;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final Connection connection;
  private final ObjectMapper mapper;
  private long records;
  private final String parserVersion;
  private final Map<String,String> filters;
  private final Map<String,Long> tableCounts = new LinkedHashMap<>();

  public DuckDbStagingWriter(Path database, ObjectMapper mapper) throws Exception { this(database, mapper, "openlibrary-parser-v1", Map.of()); }

  public DuckDbStagingWriter(Path database, ObjectMapper mapper, String parserVersion, Map<String,String> filters) throws Exception {
    this.database = database.toAbsolutePath().normalize();
    Files.createDirectories(this.database.getParent());
    this.manifest = this.database.resolveSibling(this.database.getFileName() + ".manifest.json");
    this.lockChannel = FileChannel.open(this.database.resolveSibling(this.database.getFileName() + ".lock"),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    this.lock = lockChannel.tryLock();
    if (lock == null) throw new IllegalStateException("another writer owns this DuckDB generation");
    this.connection = DriverManager.getConnection("jdbc:duckdb:" + this.database);
    this.mapper = mapper;
    this.parserVersion = parserVersion == null || parserVersion.isBlank() ? "unknown" : parserVersion;
    this.filters = filters == null ? Map.of() : Map.copyOf(filters);
    connection.createStatement().execute("PRAGMA enable_external_access=false");
    for (var table : TABLES) connection.createStatement().execute("CREATE TABLE IF NOT EXISTS " + table
        + " (source_id VARCHAR, external_id VARCHAR, revision VARCHAR, raw_locator VARCHAR, raw_sha256 VARCHAR, source_position VARCHAR, raw_payload VARCHAR)");
    writeManifest("BUILDING");
  }

  public void append(String table, StagingRecord record) throws Exception {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("unknown staging table");
    try (var statement = connection.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
      statement.setString(1, record.sourceId()); statement.setString(2, record.externalId());
      statement.setString(3, record.revision()); statement.setString(4, record.rawLocator());
      statement.setString(5, record.rawSha256()); statement.setString(6, record.sourcePosition());
      statement.setString(7, record.rawPayload()); statement.executeUpdate(); records++; tableCounts.merge(table, 1L, Long::sum);
    }
  }

  public Manifest sealReady() throws Exception {
    // DuckDB runs in auto-commit mode by default; COMMIT would fail when no
    // explicit transaction is active. CHECKPOINT makes the generation durable
    // before its manifest is published as READY.
    connection.createStatement().execute("CHECKPOINT");
    var checksum = sha256(database);
    var result = new Manifest("READY", database.toString(), records, checksum, parserVersion, filters, Map.copyOf(tableCounts));
    writeManifest(result);
    return result;
  }

  private void writeManifest(String state) throws IOException { writeManifest(new Manifest(state, database.toString(), records, null, parserVersion, filters, Map.copyOf(tableCounts))); }
  private void writeManifest(Manifest value) throws IOException {
    var temporary = manifest.resolveSibling(manifest.getFileName() + ".part");
    mapper.writeValue(temporary.toFile(), value);
    Files.move(temporary, manifest, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private static String sha256(Path file) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    try (var input = Files.newInputStream(file)) { var buffer = new byte[8192]; int n; while ((n=input.read(buffer))>=0) digest.update(buffer,0,n); }
    return HexFormat.of().formatHex(digest.digest());
  }

  public record StagingRecord(String sourceId, String externalId, String revision, String rawLocator,
      String rawSha256, String sourcePosition, String rawPayload) {}
  public record Manifest(String state, String database, long recordCount, String sha256, String parserVersion, Map<String,String> filters, Map<String,Long> tableCounts) {}

  @Override public void close() throws Exception {
    try { if (connection != null && !connection.isClosed()) connection.close(); }
    finally { if (lock != null && lock.isValid()) lock.release(); lockChannel.close(); }
  }
}
