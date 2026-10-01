package com.bookrush.bookcontentservice.api;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/content")
public class ContentController {
  private final RestClient catalog;
  public ContentController(@Value("${bookrush.catalog-url:http://catalog-service:8080}") String url) { catalog=RestClient.builder().baseUrl(url).build(); }
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
    return catalog.get().uri("/api/v1/books/{id}/reader-assets/chapters", bookId)
        .retrieve().body(Object.class);
  }

  /** Minimal Readium Web Publication Manifest bridge for an approved EPUB asset. */
  @GetMapping("/books/{bookId}/publication.json")
  public Map<String, Object> publication(@PathVariable UUID bookId) {
    Object assets = readerAssets(bookId);
    if (!(assets instanceof List<?> list)) return Map.of("metadata", Map.of("title", bookId.toString()), "readingOrder", List.of());
    for (Object value : list) {
      if (!(value instanceof Map<?, ?> asset)) continue;
      if (!"EPUB".equals(String.valueOf(asset.get("type"))) || !"PUBLIC".equals(String.valueOf(asset.get("role")))) continue;
      Object id = asset.get("id");
      if (id == null) continue;
      Object link = downloadUrl(bookId, UUID.fromString(String.valueOf(id)));
      if (link instanceof Map<?, ?> download && download.get("url") != null) {
        return Map.of("@context", "http://readium.org/webpub-manifest/context.json", "metadata", Map.of("title", bookId.toString()),
            "readingOrder", List.of(Map.of("href", download.get("url"), "type", "application/epub+zip")));
      }
    }
    return Map.of("metadata", Map.of("title", bookId.toString()), "readingOrder", List.of());
  }
}
