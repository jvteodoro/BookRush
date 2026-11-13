package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.source.GutenbergRdfParser;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class GutenbergRdfParserTest {
  @Test
  void parsesFixtureWithoutResolvingExternalEntities() throws Exception {
    try (InputStream input = getClass().getResourceAsStream("/fixtures/gutenberg/catalog.rdf")) {
      var records = new GutenbergRdfParser().parse(input, 10);
      assertEquals(1, records.size());
      assertEquals("ebooks/1342", records.get(0).externalId());
      assertEquals("Pride and Prejudice", records.get(0).title());
      assertEquals("en", records.get(0).language());
      assertEquals(java.util.List.of("Love stories"), records.get(0).subjects());
      assertEquals("https://example.test/cache/epub/1342/pg1342.epub", records.get(0).epubUrl());
    }
  }
  @Test
  void readsOnlySubjectsAndBookshelvesRegardlessOfValueAndVocabularyOrder() throws Exception {
    String xml = """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
          xmlns:dcterms="http://purl.org/dc/terms/"
          xmlns:dcam="http://purl.org/dc/dcam/"
          xmlns:pgterms="http://www.gutenberg.org/2009/pgterms/">
          <pgterms:ebook rdf:about="ebooks/46">
            <dcterms:title>A <![CDATA[Christmas]]> Carol &amp; Stories</dcterms:title>
            <dcterms:subject><rdf:Description><rdf:value>PR</rdf:value>
              <dcam:memberOf rdf:resource="http://purl.org/dc/terms/LCC"/>
            </rdf:Description></dcterms:subject>
            <dcterms:language><rdf:Description><rdf:value>en</rdf:value></rdf:Description></dcterms:language>
            <dcterms:subject><rdf:Description>
              <dcam:memberOf rdf:resource="http://purl.org/dc/terms/LCSH"/>
              <rdf:value>Christmas &amp; Ghost stories</rdf:value>
            </rdf:Description></dcterms:subject>
            <dcterms:subject><rdf:Description><rdf:value>Christmas &amp; Ghost stories</rdf:value>
            </rdf:Description></dcterms:subject>
            <pgterms:bookshelf><rdf:Description><rdf:value>Category: British Literature</rdf:value>
            </rdf:Description></pgterms:bookshelf>
            <dcterms:type><rdf:Description><rdf:value>Text</rdf:value></rdf:Description></dcterms:type>
            <dcterms:hasFormat><pgterms:file rdf:about="https://example.test/book.epub.noimages">
              <dcterms:format><rdf:Description><rdf:value>application/epub+zip</rdf:value></rdf:Description></dcterms:format>
            </pgterms:file></dcterms:hasFormat>
          </pgterms:ebook>
        </rdf:RDF>
        """;
    var record = new GutenbergRdfParser().parse(new java.io.ByteArrayInputStream(
        xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 1).getFirst();
    assertEquals("A Christmas Carol & Stories", record.title());
    assertEquals("en", record.language());
    assertEquals(java.util.List.of("Christmas & Ghost stories", "Category: British Literature"), record.subjects());
    assertEquals("https://example.test/book.epub.noimages", record.epubUrl());
  }

  @Test
  void readsBibliographicYearInsteadOfDigitalReleaseAndResetsBetweenRecords() throws Exception {
    var records = parsePublication("<pgterms:marc260>London: Example Publisher, 1899.</pgterms:marc260>");
    assertEquals(1899, records.getFirst().publicationYear());
    assertEquals("2008-08-26", records.getFirst().digitalReleaseDate());
    assertNull(records.get(1).publicationYear());
  }

  @Test
  void unknownAmbiguousAndDigitalDatesAreNotPublicationYears() throws Exception {
    for (String statement : new String[] {"", "Unknown", "London, 1899?", "1899-1900", "2008-08-26", "18999", "London, ca. 1899", "London, circa 1899"}) {
      assertNull(parsePublication("<pgterms:marc260>" + statement + "</pgterms:marc260>")
          .getFirst().publicationYear(), statement);
    }
    assertEquals(1899, parsePublication("<pgterms:marc260>London: Publisher, [1899].</pgterms:marc260>")
        .getFirst().publicationYear());
  }

  private java.util.List<GutenbergRdfParser.GutenbergRecord> parsePublication(String publication) throws Exception {
    String xml = """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
          xmlns:dcterms="http://purl.org/dc/terms/" xmlns:pgterms="http://www.gutenberg.org/2009/pgterms/">
          <pgterms:ebook rdf:about="ebooks/1"><dcterms:issued>2008-08-26</dcterms:issued>%s</pgterms:ebook>
          <pgterms:ebook rdf:about="ebooks/2"><dcterms:issued>2020-01-01</dcterms:issued></pgterms:ebook>
        </rdf:RDF>
        """.formatted(publication);
    return new GutenbergRdfParser().parse(new java.io.ByteArrayInputStream(
        xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 10);
  }

}
