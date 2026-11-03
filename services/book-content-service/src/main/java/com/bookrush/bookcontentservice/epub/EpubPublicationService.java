package com.bookrush.bookcontentservice.epub;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Reads the immutable EPUB publication structure used by Readium. */
@Service
public final class EpubPublicationService {
  private final RestClient catalog;
  private final String serviceToken;

  public EpubPublicationService(
      RestClient.Builder builder,
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String catalogUrl,
      @Value("${bookrush.catalog-service-token:}") String serviceToken) {
    this.catalog = builder.baseUrl(catalogUrl).build();
    this.serviceToken = serviceToken;
  }

  public Optional<Publication> load(UUID bookId) {
    var assets = catalog.get().uri("/api/v1/books/{id}/reader-assets", bookId)
        .header("X-Content-Service-Token", serviceToken).retrieve().body(List.class);
    if (assets == null) return Optional.empty();
    Map<?, ?> epub = ((List<?>) assets).stream().filter(Map.class::isInstance).map(value -> (Map<?, ?>) value)
        .filter(a -> "EPUB".equals(String.valueOf(a.get("type")))).findFirst().orElse(null);
    if (epub == null || epub.get("id") == null) return Optional.empty();
    byte[] bytes = catalog.get().uri("/api/v1/books/{bookId}/reader-assets/{assetId}/content",
            bookId, epub.get("id"))
        .header("X-Content-Service-Token", serviceToken)
        .retrieve().body(byte[].class);
    if (bytes == null || bytes.length == 0) return Optional.empty();
    try { return Optional.of(parse(bookId, bytes)); }
    catch (Exception e) { return Optional.empty(); }
  }

  public byte[] resource(byte[] epub, String resource) throws Exception {
    String normalized = normalize("", resource);
    if (normalized.isBlank() || normalized.contains("..")) throw new IllegalArgumentException("invalid EPUB resource");
    try (var zip = new ZipInputStream(new ByteArrayInputStream(epub))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (normalized.equals(entry.getName())) return zip.readAllBytes();
      }
    }
    throw new NoSuchElementException("EPUB resource not found");
  }

  public byte[] resource(Publication publication, String resource) throws Exception {
    String normalized = normalize("", resource);
    if (normalized.isBlank() || normalized.contains("..")) throw new IllegalArgumentException("invalid EPUB resource");
    byte[] value = publication.entries().get(normalized);
    if (value == null) throw new NoSuchElementException("EPUB resource not found");
    return value;
  }

  private Publication parse(UUID bookId, byte[] bytes) throws Exception {
    var entries = unpack(bytes);
    var container = xml(entries.get("META-INF/container.xml"));
    String opfPath = ((Element) container.getElementsByTagNameNS("*", "rootfile").item(0)).getAttribute("full-path");
    var opf = xml(entries.get(opfPath));
    String base = directory(opfPath);
    var manifest = manifest(opf, base);
    var spine = spine(opf, manifest);
    List<Nav> toc = List.of();
    String tocId = ((Element) opf.getElementsByTagNameNS("*", "spine").item(0)).getAttribute("toc");
    if (!tocId.isBlank() && manifest.containsKey(tocId)) toc = ncx(entries.get(manifest.get(tocId).href()), base);
    if (toc.isEmpty()) {
      var nav = manifest.values().stream().filter(i -> i.properties().contains("nav")).findFirst();
      if (nav.isPresent()) toc = nav(entries.get(nav.get().href()), base);
    }
    return new Publication(bookId, entries, manifest, spine, toc);
  }

  private static Map<String, byte[]> unpack(byte[] bytes) throws Exception {
    var result = new LinkedHashMap<String, byte[]>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry; long total = 0;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        byte[] value = zip.readAllBytes(); total += value.length;
        if (total > 256L * 1024 * 1024) throw new IllegalArgumentException("EPUB too large");
        result.put(entry.getName(), value);
      }
    }
    return result;
  }

  private static Map<String, Item> manifest(org.w3c.dom.Document doc, String base) {
    var result = new LinkedHashMap<String, Item>();
    var nodes = doc.getElementsByTagNameNS("*", "item");
    for (int i = 0; i < nodes.getLength(); i++) {
      var e = (Element) nodes.item(i);
      result.put(e.getAttribute("id"), new Item(normalize(base, e.getAttribute("href")), e.getAttribute("media-type"), e.getAttribute("properties")));
    }
    return result;
  }

  private static List<Item> spine(org.w3c.dom.Document doc, Map<String, Item> manifest) {
    var result = new ArrayList<Item>();
    var nodes = doc.getElementsByTagNameNS("*", "itemref");
    for (int i = 0; i < nodes.getLength(); i++) {
      Item item = manifest.get(((Element) nodes.item(i)).getAttribute("idref"));
      if (item != null && item.mediaType().contains("html")) result.add(item);
    }
    return result;
  }

  private static List<Nav> ncx(byte[] bytes, String base) throws Exception {
    var result = new ArrayList<Nav>();
    var map = xml(bytes).getElementsByTagNameNS("*", "navMap").item(0);
    if (map != null) visitNcx(map, null, result, base);
    return result;
  }

  private static void visitNcx(Node parent, String parentKey, List<Nav> result, String base) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element e) || !"navPoint".equals(e.getLocalName())) continue;
      String key = "toc-" + (result.size() + 1);
      String title = descendantText(e, "navLabel"); String src = descendantAttr(e, "content", "src");
      var target = target(src, base);
      result.add(new Nav(key, parentKey, title, target.href(), target.fragment()));
      visitNcx(e, key, result, base);
    }
  }

  private static List<Nav> nav(byte[] bytes, String base) throws Exception {
    var result = new ArrayList<Nav>();
    var doc = xml(bytes); var nodes = doc.getElementsByTagNameNS("*", "nav");
    for (int i = 0; i < nodes.getLength(); i++) {
      var nav = (Element) nodes.item(i);
      if (!nav.getAttributeNS("http://www.idpf.org/2007/ops", "type").contains("toc") && !"toc".equals(nav.getAttribute("id"))) continue;
      visitHtmlNav(nav, null, result, base); break;
    }
    return result;
  }

  private static void visitHtmlNav(Node parent, String parentKey, List<Nav> result, String base) {
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (!(node instanceof Element e)) continue;
      if ("a".equals(e.getLocalName())) {
        String key = "toc-" + (result.size() + 1); var target = target(e.getAttribute("href"), base);
        result.add(new Nav(key, parentKey, e.getTextContent().trim(), target.href(), target.fragment()));
      } else if ("ol".equals(e.getLocalName()) || "li".equals(e.getLocalName())) visitHtmlNav(e, parentKey, result, base);
    }
  }

  public static String resourceHref(UUID bookId, String path) {
    return "/api/v1/content/books/" + bookId + "/resources/" + path;
  }

  private static Target target(String value, String base) {
    int hash = value == null ? -1 : value.indexOf('#');
    String path = hash < 0 ? value : value.substring(0, hash);
    return new Target(normalize(base, path), hash < 0 ? null : value.substring(hash + 1));
  }
  private static String descendantText(Element e, String name) { var n = e.getElementsByTagNameNS("*", name); return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim(); }
  private static String descendantAttr(Element e, String name, String attr) { var n = e.getElementsByTagNameNS("*", name); return n.getLength() == 0 ? "" : ((Element)n.item(0)).getAttribute(attr); }
  private static org.w3c.dom.Document xml(byte[] bytes) throws Exception {
    var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true); f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); f.setFeature("http://xml.org/sax/features/external-general-entities", false); f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); f.setXIncludeAware(false); f.setExpandEntityReferences(false); return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }
  private static String directory(String p) { int i = p.lastIndexOf('/'); return i < 0 ? "" : p.substring(0, i + 1); }
  private static String normalize(String base, String path) { var parts = new ArrayDeque<String>(); for (String s : (base + URLDecoder.decode(path == null ? "" : path.replace('\\','/'), StandardCharsets.UTF_8)).split("/")) { if (s.isBlank() || ".".equals(s)) continue; if ("..".equals(s)) { if (!parts.isEmpty()) parts.removeLast(); } else parts.add(s); } return String.join("/", parts); }

  public record Publication(UUID bookId, Map<String, byte[]> entries, Map<String, Item> manifest, List<Item> spine, List<Nav> toc) {}
  public record Item(String href, String mediaType, String properties) {}
  public record Nav(String key, String parent, String title, String href, String fragment) {}
  private record Target(String href, String fragment) {}
}
