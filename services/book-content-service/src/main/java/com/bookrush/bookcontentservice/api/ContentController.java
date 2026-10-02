package com.bookrush.bookcontentservice.api;

import java.util.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.bookrush.bookcontentservice.storage.ReaderArtifactStore;

@RestController
@RequestMapping("/api/v1/content")
public class ContentController {
  private final RestClient catalog;
  private final String catalogServiceToken;
  private final ReaderArtifactStore artifactStore;
  public ContentController(
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String url,
      @Value("${bookrush.catalog-service-token:}") String catalogServiceToken,
      ReaderArtifactStore artifactStore) {
    catalog=RestClient.builder().baseUrl(url).build();
    this.catalogServiceToken = catalogServiceToken;
    this.artifactStore = artifactStore;
  }
  @GetMapping("/books/{bookId}")
  public Map<String,Object> book(@PathVariable UUID bookId) {
    Map<?,?> page=catalog.get().uri("/api/v1/books?page=0&size=100").retrieve().body(Map.class);
    Object found=null;
    if(page!=null && page.get("items") instanceof List<?> items) for(Object item:items) if(item instanceof Map<?,?> m && bookId.toString().equals(String.valueOf(m.get("id")))) found=item;
    if(found==null) return Map.of("bookId",bookId,"status","CONTENT_NOT_AVAILABLE");
    return Map.of("book",found,"bookId",bookId,"status","CATALOG_METADATA_AVAILABLE","source","catalog-service");
  }
  @GetMapping("/books/{bookId}/assets")
  public Object readerAssets(@PathVariable UUID bookId) {
    return catalog.get().uri("/api/v1/books/{id}/reader-assets", bookId).retrieve().body(Object.class);
  }
  @GetMapping("/books/{bookId}/assets/{assetId}/download-url")
  public Object downloadUrl(@PathVariable UUID bookId, @PathVariable UUID assetId) {
    return catalog.get().uri("/api/v1/books/{bookId}/reader-assets/{assetId}/download-url", bookId, assetId).retrieve().body(Object.class);
  }

  @GetMapping("/books/{bookId}/chapters")
  public Object chapters(@PathVariable UUID bookId) {
    return chaptersFor(bookId);
  }

  /** Web Publication Manifest consumed by the Readium navigator in the web client. */
  @GetMapping("/books/{bookId}/publication.json")
  public Map<String, Object> publication(@PathVariable UUID bookId) {
    List<Map<String, Object>> chapters = chaptersFor(bookId);
    var readingOrder = new ArrayList<Map<String, Object>>();
    var toc = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> chapter : chapters) {
      String id = String.valueOf(chapter.get("id"));
      String title = String.valueOf(chapter.getOrDefault("title", chapter.getOrDefault("chapter_key", id)));
      String href = "/api/v1/content/books/" + bookId + "/chapters/" + id + "/content";
      var link = new LinkedHashMap<String, Object>();
      link.put("href", href); link.put("title", title); link.put("type", "text/html");
      readingOrder.add(link);
      toc.add(link);
    }
    var manifest = new LinkedHashMap<String, Object>();
    manifest.put("@context", "http://readium.org/webpub-manifest/context.json");
    manifest.put("metadata", metadata(bookId));
    manifest.put("readingOrder", readingOrder);
    manifest.put("toc", toc);
    String artifactKey = artifactStore.persist(bookId.toString(), manifest);
    if (artifactKey != null) manifest.put("bookrushArtifactKey", artifactKey);
    return manifest;
  }

  @GetMapping(value = "/books/{bookId}/chapters/{chapterId}/content", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> chapterContent(@PathVariable UUID bookId, @PathVariable UUID chapterId) {
    Map<String, Object> chapter = chaptersFor(bookId).stream().filter(c -> chapterId.toString().equals(String.valueOf(c.get("id")))).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found"));
    UUID version = UUID.fromString(String.valueOf(chapter.get("text_asset_version_id")));
    String text = catalog.get().uri("/api/v1/books/{bookId}/reader-assets/text-versions/{versionId}/content", bookId, version)
        .header("X-Content-Service-Token", catalogServiceToken).retrieve().body(String.class);
    if (text == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Text artifact unavailable");
    int count = text.codePointCount(0, text.length());
    int start = Math.min(Math.max(((Number) chapter.getOrDefault("start_offset", 0)).intValue(), 0), count);
    int end = Math.min(Math.max(((Number) chapter.getOrDefault("end_offset", start)).intValue(), start), count);
    String chapterText = sliceCodePoints(text == null ? "" : text, start, end);
    String body = Arrays.stream(chapterText.split("\\R\\s*\\R", -1)).map(String::trim).filter(s -> !s.isBlank())
        .map(s -> "<p>" + escape(s).replace("\\n", "<br>") + "</p>").reduce("", String::concat);
    String title = escape(String.valueOf(chapter.getOrDefault("title", "Chapter")));
    String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + title + "</title><style>"
        + ":root{color-scheme:light}*{box-sizing:border-box}html{background:#f7f4ee}body{color:#29261f;background:#f7f4ee;font-family:Georgia,\"Times New Roman\",serif;font-size:clamp(1.08rem,1.25vw,1.32rem);line-height:1.82;max-width:46rem;margin:0 auto;padding:clamp(3.5rem,9vh,7rem) clamp(1.25rem,4vw,3rem) 8rem}h1{font-size:clamp(1.8rem,3vw,2.6rem);font-weight:600;line-height:1.2;text-align:center;margin:0 0 3rem;color:#342f28}p{margin:0 0 1.35em;text-wrap:pretty}p:first-of-type:first-letter{float:left;font-size:4.1em;line-height:.8;padding:0 .1em 0 0;color:#6c5d4c}@media(max-width:640px){body{font-size:1.08rem;line-height:1.76;padding:3.5rem 1.2rem 6rem}h1{margin-bottom:2.4rem}}"
        + "</style></head><body><h1>" + title + "</h1>" + body + "</body></html>";
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(html);
  }

  private List<Map<String, Object>> chaptersFor(UUID bookId) {
    Object value = catalog.get().uri("/api/v1/books/{id}/reader-assets/chapters", bookId).retrieve().body(Object.class);
    if (!(value instanceof List<?> list)) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    for (Object valueItem : list) {
      if (valueItem instanceof Map<?, ?> map) {
        var item = new LinkedHashMap<String, Object>();
        map.forEach((k, val) -> item.put(String.valueOf(k), val));
        result.add(item);
      }
    }
    return result;
  }

  private Map<String, Object> metadata(UUID bookId) {
    Object value = book(bookId).get("book");
    if (value instanceof Map<?, ?> map) {
      Object title = map.containsKey("canonicalTitle") ? map.get("canonicalTitle") : (map.containsKey("canonical_title") ? map.get("canonical_title") : bookId.toString());
      return Map.of("title", String.valueOf(title), "identifier", bookId.toString(), "language", "und");
    }
    return Map.of("title", bookId.toString(), "identifier", bookId.toString(), "language", "und");
  }

  private static String sliceCodePoints(String value, int start, int end) {
    int a = value.offsetByCodePoints(0, start); int b = value.offsetByCodePoints(0, end); return value.substring(a, b);
  }
  private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
