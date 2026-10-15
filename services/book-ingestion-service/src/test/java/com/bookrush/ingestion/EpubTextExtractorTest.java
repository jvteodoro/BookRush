package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.processing.EpubTextExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class EpubTextExtractorTest {
  @Test
  void followsSpineAndPreservesNestedNcxStructure() throws Exception {
    Path epub = Files.createTempFile("structured-", ".epub");
    try (var output = new ZipOutputStream(Files.newOutputStream(epub))) {
      put(output, "META-INF/container.xml", """
          <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
          """);
      put(output, "OEBPS/content.opf", """
          <package xmlns="http://www.idpf.org/2007/opf"><manifest>
            <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
          </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
          """);
      put(output, "OEBPS/toc.ncx", """
          <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
            <navPoint><navLabel><text>PART ONE</text></navLabel><content src="chapter.xhtml#part"/>
              <navPoint><navLabel><text>Chapter I</text></navLabel><content src="chapter.xhtml#chapter"/></navPoint>
            </navPoint>
          </navMap></ncx>
          """);
      put(output, "OEBPS/chapter.xhtml", """
          <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <h1 id="part">PART ONE</h1><h2 id="chapter">Chapter I</h2><p>Text with structure.</p>
          </body></html>
          """);
    }

    try {
      var extracted = new EpubTextExtractor().extractStructured(epub);
      assertEquals(2, extracted.chapters().size());
      assertEquals("PART ONE", extracted.chapters().get(0).title());
      assertEquals("Chapter I", extracted.chapters().get(1).title());
      assertEquals("toc-1", extracted.chapters().get(1).parent());
      assertTrue(extracted.chapters().get(0).end() <= extracted.chapters().get(1).start());
      assertTrue(extracted.text().contains("Text with structure."));
      int length = extracted.text().codePointCount(0, extracted.text().length());
      assertTrue(extracted.chapters().stream().allMatch(c -> c.start() >= 0 && c.end() <= length));
    } finally {
      Files.deleteIfExists(epub);
    }
  }

  private static void put(ZipOutputStream output, String name, String value) throws Exception {
    output.putNextEntry(new ZipEntry(name));
    output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    output.closeEntry();
  }
}
