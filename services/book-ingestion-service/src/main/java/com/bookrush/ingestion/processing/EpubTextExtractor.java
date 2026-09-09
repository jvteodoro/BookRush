package com.bookrush.ingestion.processing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipFile;

/** Extracts XHTML resources in archive order; validation happens before extraction. */
public final class EpubTextExtractor {
  private final HtmlTextExtractor html = new HtmlTextExtractor();

  public String extract(Path epub) throws Exception {
    var parts = new ArrayList<String>();
    try (var zip = new ZipFile(epub.toFile())) {
      var entries = zip.stream().filter(e -> !e.isDirectory() && (e.getName().endsWith(".xhtml") || e.getName().endsWith(".html")))
          .sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName)).toList();
      for (var entry : entries) {
        try (var input = zip.getInputStream(entry)) { parts.add(html.extract(new String(input.readAllBytes(), StandardCharsets.UTF_8))); }
      }
    }
    return new PlainTextNormalizer().normalize(String.join("\n", parts));
  }
}
