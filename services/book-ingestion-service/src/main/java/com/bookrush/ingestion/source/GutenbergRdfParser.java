package com.bookrush.ingestion.source;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/** Streaming parser for the official Gutenberg RDF catalog; it never resolves external entities. */
public final class GutenbergRdfParser {
  private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String DC = "http://purl.org/dc/terms/";
  private static final String PG = "http://www.gutenberg.org/2009/pgterms/";
  private static final String DCAM = "http://purl.org/dc/dcam/";

  public List<GutenbergRecord> parse(InputStream input, int maxRecords) throws Exception {
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    XMLStreamReader reader = factory.createXMLStreamReader(input);
    var records = new ArrayList<GutenbergRecord>();
    var path = new ArrayDeque<QName>();
    var text = new StringBuilder();
    String id = null, title = null, language = null, rights = null, issued = null,
        creator = null, formatUrl = null, currentFileAbout = null,
        subjectValue = null, subjectVocabulary = null;
    var subjects = new ArrayList<String>();
    Integer publicationYear = null;
    try {
      while (reader.hasNext() && records.size() < maxRecords) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          QName element = reader.getName();
          path.push(element);
          text.setLength(0);
          if (element.equals(new QName(PG, "ebook"))) {
            id = reader.getAttributeValue(RDF, "about");
            title = language = rights = issued = creator = formatUrl = null;
            subjects.clear();
            publicationYear = null;
          } else if (element.equals(new QName(PG, "file"))) {
            currentFileAbout = reader.getAttributeValue(RDF, "about");
          } else if (element.equals(new QName(DC, "subject"))) {
            subjectValue = subjectVocabulary = null;
          } else if (element.equals(new QName(DCAM, "memberOf"))
              && path.contains(new QName(DC, "subject"))) {
            subjectVocabulary = reader.getAttributeValue(RDF, "resource");
          }
        } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
          text.append(reader.getText());
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          QName element = reader.getName();
          String value = text.toString().trim();
          if (!value.isEmpty()) {
            if (element.equals(new QName(DC, "title"))) title = value;
            else if (element.equals(new QName(DC, "rights"))) rights = value;
            else if (element.equals(new QName(DC, "issued"))) issued = value;
            else if (element.equals(new QName(PG, "marc260"))) publicationYear = publicationYear(value);
            else if (element.equals(new QName(PG, "name"))
                && path.contains(new QName(DC, "creator"))) creator = value;
            else if (element.equals(new QName(RDF, "value"))) {
              if (path.contains(new QName(DC, "language"))) language = value;
              else if (path.contains(new QName(DC, "subject"))) subjectValue = value;
              else if (path.contains(new QName(PG, "bookshelf")) && !subjects.contains(value)) subjects.add(value);
            }
          }
          if (element.equals(new QName(DC, "subject")) && subjectValue != null
              && (subjectVocabulary == null || subjectVocabulary.equals("http://purl.org/dc/terms/LCSH"))
              && !subjects.contains(subjectValue)) subjects.add(subjectValue);
          if (element.equals(new QName(PG, "file"))) {
            if (currentFileAbout != null
                && currentFileAbout.toLowerCase(java.util.Locale.ROOT).contains(".epub")) {
              if (formatUrl == null
                  || currentFileAbout.toLowerCase(java.util.Locale.ROOT).contains(".epub.noimages"))
                formatUrl = currentFileAbout;
            }
            currentFileAbout = null;
          }
          if (element.equals(new QName(PG, "ebook")) && id != null) {
            records.add(new GutenbergRecord(id, title, language, creator, rights, issued,
                formatUrl, List.copyOf(subjects), publicationYear));
          }
          path.pop();
          text.setLength(0);
        }
      }
    } finally {
      reader.close();
    }
    return List.copyOf(records);
  }

  /** Only a single explicit bibliographic year is safe; ranges/uncertain dates stay unknown. */
  private static Integer publicationYear(String statement) {
    var match = java.util.regex.Pattern.compile("(?<![0-9])([1-9][0-9]{3})(?![0-9])").matcher(statement);
    if (!match.find()) return null;
    if (statement.contains("?") || statement.substring(0, match.start())
        .matches("(?is).*\\b(?:circa|ca\\.?|c\\.|approximately)\\s*$")) return null;
    int year = Integer.parseInt(match.group(1));
    int end = match.end();
    if (match.find()) return null;
    String suffix = statement.substring(end).trim();
    return suffix.matches("[\\] .]*") ? year : null;
  }

  public record GutenbergRecord(
      String externalId,
      String title,
      String language,
      String creator,
      String rights,
      String digitalReleaseDate,
      String epubUrl,
      List<String> subjects,
      Integer publicationYear) {}
}
