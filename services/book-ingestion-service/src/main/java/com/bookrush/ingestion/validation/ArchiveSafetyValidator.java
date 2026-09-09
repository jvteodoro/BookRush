package com.bookrush.ingestion.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Bounded ZIP/EPUB inspection before any parser sees untrusted content. */
public final class ArchiveSafetyValidator {
  private final long maxUncompressedBytes;
  private final int maxEntries;
  private final int maxCompressionRatio;

  public ArchiveSafetyValidator(long maxUncompressedBytes, int maxEntries, int maxCompressionRatio) {
    this.maxUncompressedBytes = maxUncompressedBytes;
    this.maxEntries = maxEntries;
    this.maxCompressionRatio = maxCompressionRatio;
  }

  public void validateZip(Path file) throws IOException {
    try (var zip = new ZipFile(file.toFile())) {
      long total = 0; int entries = 0;
      var iterator = zip.entries();
      while (iterator.hasMoreElements()) {
        var entry = iterator.nextElement(); entries++;
        if (entries > maxEntries) throw new IllegalArgumentException("archive entry limit exceeded");
        var path = Path.of(entry.getName()).normalize();
        if (path.isAbsolute() || path.startsWith("..") || entry.getName().contains("\\")) {
          throw new IllegalArgumentException("archive path traversal");
        }
        // ZipEntry does not expose Unix mode portably; the extraction layer must
        // also reject symlink entries using the archive implementation's attrs.
        long size = Math.max(0, entry.getSize()); total += size;
        if (total > maxUncompressedBytes) throw new IllegalArgumentException("archive uncompressed limit exceeded");
        if (entry.getCompressedSize() > 0 && size / entry.getCompressedSize() > maxCompressionRatio) {
          throw new IllegalArgumentException("archive compression ratio exceeded");
        }
      }
    }
  }

  public void validateEpub(Path file) throws IOException {
    validateZip(file);
    try (var zip = new ZipFile(file.toFile())) {
      var mimetype = zip.getEntry("mimetype");
      if (mimetype == null || !new String(zip.getInputStream(mimetype).readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)
          .trim().equals("application/epub+zip")) throw new IllegalArgumentException("invalid EPUB mimetype");
      if (zip.getEntry("META-INF/container.xml") == null) throw new IllegalArgumentException("EPUB container.xml missing");
      boolean opf = zip.stream().anyMatch(e -> e.getName().toLowerCase(Locale.ROOT).endsWith(".opf"));
      if (!opf) throw new IllegalArgumentException("EPUB package document missing");
    }
  }

  public static void validateHtml(String html) {
    var lower = html.toLowerCase(Locale.ROOT);
    if (lower.contains("<script") || lower.contains("javascript:") || lower.matches("(?s).*\\b(?:src|href)\\s*=\\s*[\\\"']https?://.*")) {
      throw new IllegalArgumentException("HTML scripts and external resources are not allowed");
    }
  }
}
