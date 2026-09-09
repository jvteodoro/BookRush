package com.bookrush.ingestion.source;

import com.bookrush.ingestion.storage.RawObjectStore;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Streams a snapshot to a same-filesystem .part file before publishing it to RAW. */
public final class SnapshotDownloader {
  private final HttpClient client;
  private final SourceUrlPolicy urlPolicy;
  private final long maxBytes;

  public SnapshotDownloader(HttpClient client, SourceUrlPolicy urlPolicy, long maxBytes) {
    this.client = client;
    this.urlPolicy = urlPolicy;
    this.maxBytes = maxBytes;
  }

  public SnapshotResult download(URI requested, Path target, String objectKey,
      String expectedSha256, RawObjectStore raw) throws Exception {
    urlPolicy.validate(requested, 0);
    Files.createDirectories(target.toAbsolutePath().normalize().getParent());
    Path part = target.resolveSibling(target.getFileName() + ".part");
    long offset = Files.exists(part) ? Files.size(part) : 0;
    HttpRequest.Builder builder = HttpRequest.newBuilder(requested).timeout(Duration.ofMinutes(2)).GET();
    if (offset > 0) builder.header("Range", "bytes=" + offset + "-");
    var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() == 416 && offset > 0) {
      Files.deleteIfExists(part);
      return download(requested, target, objectKey, expectedSha256, raw);
    }
    if (response.statusCode() != 200 && response.statusCode() != 206) {
      throw new IllegalStateException("snapshot HTTP status " + response.statusCode());
    }
    boolean append = offset > 0 && response.statusCode() == 206;
    if (!append) { Files.deleteIfExists(part); offset = 0; }
    long total = offset;
    try (InputStream input = response.body(); var output = Files.newOutputStream(part,
        append ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
            : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING})) {
      var buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) throw new IllegalStateException("snapshot exceeds configured dataset budget");
        output.write(buffer, 0, read);
      }
      output.flush();
    }
    var hash = sha256(part);
    if (expectedSha256 != null && !expectedSha256.equals(hash)) throw new IllegalStateException("snapshot checksum mismatch");
    try { Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
    catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(part, target, StandardCopyOption.REPLACE_EXISTING); }
    try (InputStream content = Files.newInputStream(target)) { raw.put(objectKey, content, total, hash); }
    return new SnapshotResult(requested, target, objectKey, total, hash, response.headers().firstValue("ETag").orElse(null));
  }

  private static String sha256(Path file) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(file)) {
      var buffer = new byte[8192]; int read;
      while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  public record SnapshotResult(URI requestedUrl, Path localFile, String objectKey,
      long sizeBytes, String sha256, String etag) {}
}
