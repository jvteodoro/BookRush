package com.bookrush.ingestion.processing;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Extracts an EPUB following its publication structure instead of ZIP entry order.
 * The OPF spine defines reading order and the NCX/NAV defines the hierarchical TOC.
 */
public final class EpubTextExtractor {
  private final HtmlTextExtractor html = new HtmlTextExtractor();
  private final PlainTextNormalizer normalizer = new PlainTextNormalizer();
  private static final Pattern ANCHOR = Pattern.compile(
      "(?is)\\b(?:id|name)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']");

  public String extract(Path epub) throws Exception {
    return extractStructured(epub).text();
  }

  public ExtractedEpub extractStructured(Path epub) throws Exception {
    var parts = new ArrayList<String>();
    try (var zip = new ZipFile(epub.toFile())) {
      String opfPath = parseContainer(zip);
      var opf = parseXml(zip.getInputStream(zip.getEntry(opfPath)).readAllBytes());
      String opfDirectory = directory(opfPath);
      var manifest = parseManifest(opf, opfDirectory);
      String ncxPath = ncxPath(opf, manifest, opfDirectory);
      var entries = ncxPath == null ? List.<NavEntry>of() : parseNcx(zip, ncxPath, opfDirectory);
      var spine = parseSpine(opf, manifest);
      var chunks = new ArrayList<Chunk>();
      int offset = 0;
      for (var item : spine) {
        var archiveEntry = zip.getEntry(item.href());
        if (archiveEntry == null) continue;
        String source;
        try (var input = zip.getInputStream(archiveEntry)) {
          source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String text = normalizer.normalize(html.extract(source));
        int start = offset;
        if (!parts.isEmpty()) {
          parts.add("\n");
          offset++;
          start++;
        }
        parts.add(text);
        offset += text.codePointCount(0, text.length());
        chunks.add(new Chunk(item.href(), source, start, offset));
      }

      String extractedText = String.join("", parts);
      String text = normalizer.normalize(extractedText);
      var chapters = chapters(entries, chunks, extractedText);
      if (chapters.isEmpty()) chapters = fallbackChapters(chunks, extractedText);
      chapters = remapOffsets(chapters, extractedText, text);
      return new ExtractedEpub(text, chapters);
    }
  }

  private List<Chapter> remapOffsets(List<Chapter> chapters, String source, String normalized) {
    int normalizedLength = normalized.codePointCount(0, normalized.length());
    return chapters.stream()
        .map(
            chapter ->
                new Chapter(
                    chapter.key(),
                    chapter.parent(),
                    chapter.position(),
                    chapter.title(),
                    remapOffset(source, normalized, chapter.start()),
                    remapOffset(source, normalized, chapter.end()),
                    chapter.sourceHref(),
                    chapter.confidence()))
        .map(
            chapter ->
                chapter.end() < chapter.start()
                    ? new Chapter(
                        chapter.key(),
                        chapter.parent(),
                        chapter.position(),
                        chapter.title(),
                        chapter.start(),
                        Math.min(normalizedLength, chapter.start()),
                        chapter.sourceHref(),
                        chapter.confidence())
                    : chapter)
        .toList();
  }

  private int remapOffset(String source, String normalized, int codePointOffset) {
    int sourceLength = source.codePointCount(0, source.length());
    int bounded = Math.max(0, Math.min(codePointOffset, sourceLength));
    if (bounded == sourceLength) return normalized.codePointCount(0, normalized.length());
    int charOffset = source.offsetByCodePoints(0, bounded);
    String normalizedPrefix = normalizer.normalize(source.substring(0, charOffset));
    return Math.min(
        normalized.codePointCount(0, normalized.length()),
        normalizedPrefix.codePointCount(0, normalizedPrefix.length()));
  }

  private List<Chapter> chapters(List<NavEntry> entries, List<Chunk> chunks, String text) {
    var result = new ArrayList<Chapter>();
    var byHref = new HashMap<String, Chunk>();
    chunks.forEach(c -> byHref.put(c.href(), c));
    var starts = new HashMap<NavEntry, Integer>();
    for (var entry : entries) {
      Chunk chunk = byHref.get(entry.href());
      if (chunk == null) continue;
      int start = chunk.start();
      if (entry.fragment() != null) {
        Matcher matcher = ANCHOR.matcher(chunk.source());
        while (matcher.find()) {
          if (entry.fragment().equals(matcher.group(1))) {
            String prefix = html.extract(chunk.source().substring(0, matcher.start()));
            start += normalizer.normalize(prefix).codePointCount(0, normalizer.normalize(prefix).length());
            break;
          }
        }
      }
      starts.put(entry, Math.min(Math.max(start, chunk.start()), chunk.end()));
    }
    for (int i = 0; i < entries.size(); i++) {
      var entry = entries.get(i);
      Integer start = starts.get(entry);
      if (start == null) continue;
      int end = byHref.get(entry.href()).end();
      for (int j = i + 1; j < entries.size(); j++) {
        Integer next = starts.get(entries.get(j));
        if (next != null && entries.get(j).href().equals(entry.href()) && next > start) {
          end = next;
          break;
        }
      }
      result.add(new Chapter(entry.key(), entry.parent(), result.size(), entry.title(), start, end, entry.href(), "EXPLICIT"));
    }
    return result;
  }

  private List<Chapter> fallbackChapters(List<Chunk> chunks, String text) {
    var result = new ArrayList<Chapter>();
    for (int i = 0; i < chunks.size(); i++) {
      var chunk = chunks.get(i);
      result.add(new Chapter("document-" + i, null, i, "Document " + (i + 1), chunk.start(), chunk.end(), chunk.href(), "EXPLICIT"));
    }
    return result.isEmpty()
        ? List.of(new Chapter("document", null, 0, "DOCUMENT", 0, text.codePointCount(0, text.length()), null, "FALLBACK"))
        : result;
  }

  private static String parseContainer(ZipFile zip) throws Exception {
    var root = parseXml(zip.getInputStream(zip.getEntry("META-INF/container.xml")).readAllBytes());
    return ((Element) root.getElementsByTagNameNS("*", "rootfile").item(0)).getAttribute("full-path");
  }

  private static Map<String, Item> parseManifest(org.w3c.dom.Document doc, String dir) {
    var result = new LinkedHashMap<String, Item>();
    var nodes = doc.getElementsByTagNameNS("*", "item");
    for (int i = 0; i < nodes.getLength(); i++) {
      var item = (Element) nodes.item(i);
      result.put(item.getAttribute("id"), new Item(resolve(dir, item.getAttribute("href")), item.getAttribute("media-type"), item.getAttribute("properties")));
    }
    return result;
  }

  private static String ncxPath(org.w3c.dom.Document doc, Map<String, Item> manifest, String dir) {
    var spine = (Element) doc.getElementsByTagNameNS("*", "spine").item(0);
    if (spine == null) return null;
    Item item = manifest.get(spine.getAttribute("toc"));
    if (item != null) return item.href();
    return manifest.values().stream().filter(i -> i.mediaType().contains("dtbncx")).map(Item::href).findFirst().orElse(null);
  }

  private static List<Item> parseSpine(org.w3c.dom.Document doc, Map<String, Item> manifest) {
    var result = new ArrayList<Item>();
    var nodes = doc.getElementsByTagNameNS("*", "itemref");
    for (int i = 0; i < nodes.getLength(); i++) {
      var item = manifest.get(((Element) nodes.item(i)).getAttribute("idref"));
      if (item != null && (item.mediaType().contains("html") || item.mediaType().contains("xhtml"))) result.add(item);
    }
    return result;
  }

  private static List<NavEntry> parseNcx(ZipFile zip, String path, String opfDirectory) throws Exception {
    var doc = parseXml(zip.getInputStream(zip.getEntry(path)).readAllBytes());
    var result = new ArrayList<NavEntry>();
    var navMap = doc.getElementsByTagNameNS("*", "navMap").item(0);
    if (navMap != null) visitNav(navMap, null, result, opfDirectory);
    return result;
  }

  private static void visitNav(Node parent, String parentKey, List<NavEntry> result, String dir) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element e) || !"navPoint".equals(e.getLocalName())) continue;
      String key = "toc-" + (result.size() + 1);
      String title = textOf(e, "navLabel");
      String src = textOf(e, "content");
      String href = src;
      String fragment = null;
      int hash = src.indexOf('#');
      if (hash >= 0) { href = src.substring(0, hash); fragment = src.substring(hash + 1); }
      result.add(new NavEntry(key, parentKey, title.isBlank() ? key : title, resolve(dir, href), fragment));
      visitNav(e, key, result, dir);
    }
  }

  private static String textOf(Element parent, String localName) {
    var nodes = parent.getElementsByTagNameNS("*", localName);
    if (nodes.getLength() == 0) return "";
    var node = (Element) nodes.item(0);
    return node.hasAttribute("src") ? node.getAttribute("src") : node.getTextContent().trim();
  }

  private static org.w3c.dom.Document parseXml(byte[] bytes) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  private static String directory(String path) { int slash = path.lastIndexOf('/'); return slash < 0 ? "" : path.substring(0, slash + 1); }
  private static String resolve(String base, String path) {
    String value = URLDecoder.decode(path.replace('\\', '/'), StandardCharsets.UTF_8);
    var segments = new ArrayDeque<String>();
    for (String segment : (base + value).split("/")) {
      if (segment.isBlank() || ".".equals(segment)) continue;
      if ("..".equals(segment)) { if (!segments.isEmpty()) segments.removeLast(); } else segments.add(segment);
    }
    return String.join("/", segments);
  }

  private record Item(String href, String mediaType, String properties) {}
  private record Chunk(String href, String source, int start, int end) {}
  private record NavEntry(String key, String parent, String title, String href, String fragment) {}
  public record Chapter(String key, String parent, int position, String title, int start, int end, String sourceHref, String confidence) {}
  public record ExtractedEpub(String text, List<Chapter> chapters) {}
}
