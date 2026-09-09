package com.bookrush.ingestion.source;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/** Streaming parser for the official Gutenberg RDF catalog; it never resolves external entities. */
public final class GutenbergRdfParser {
  public List<GutenbergRecord> parse(InputStream input, int maxRecords) throws Exception {
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    XMLStreamReader reader = factory.createXMLStreamReader(input);
    var records = new ArrayList<GutenbergRecord>();
    String id = null, title = null, language = null, rights = null, issued = null, creator = null, formatUrl = null;
    String currentFileAbout = null;
    var subjects = new ArrayList<String>();
    String element = null;
    while (reader.hasNext() && records.size() < maxRecords) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        element = reader.getLocalName();
        if ("ebook".equals(element)) {
          id = reader.getAttributeValue(null, "about"); title = language = rights = issued = creator = formatUrl = null; subjects.clear();
        } else if ("file".equals(element)) {
          currentFileAbout = reader.getAttributeValue(null, "about");
        }
      } else if (event == XMLStreamConstants.CHARACTERS && element != null) {
        var text = reader.getText().trim();
        if (!text.isEmpty()) {
          switch (element) {
            case "title" -> title = text;
            case "value" -> { if (language == null && text.length() <= 3) language = text; else if (!subjects.contains(text)) subjects.add(text); }
            case "rights" -> rights = text;
            case "issued" -> issued = text;
            case "name" -> creator = text;
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if ("file".equals(reader.getLocalName())) {
          if (currentFileAbout != null && currentFileAbout.endsWith(".epub")) formatUrl = currentFileAbout;
        }
        if ("ebook".equals(reader.getLocalName()) && id != null) {
          records.add(new GutenbergRecord(id, title, language, creator, rights, issued, formatUrl, List.copyOf(subjects)));
        }
        element = null;
      }
    }
    reader.close();
    return List.copyOf(records);
  }

  public record GutenbergRecord(String externalId, String title, String language, String creator,
      String rights, String digitalReleaseDate, String epubUrl, List<String> subjects) {}
}
