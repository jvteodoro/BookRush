package com.bookrush.catalog.asset;

import io.swagger.v3.oas.annotations.Operation;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
@ConditionalOnProperty(name="storage.enabled", havingValue="true")
@RequestMapping("/api/v1/books/{bookId}/reader-assets")
public class ReaderContentController {
  private final AssetService service;
  private final JdbcTemplate jdbc;
  public ReaderContentController(AssetService service, JdbcTemplate jdbc) {
    this.service = service;
    this.jdbc = jdbc;
  }
  @GetMapping
  @Operation(summary = "Listar assets reader-ready")
  public List<AssetService.AssetView> list(@PathVariable UUID bookId) {
    return service.list(bookId, 0).stream()
        .filter(a -> a.status().name().equals("ACTIVE"))
        .toList();
  }

  @GetMapping("/{assetId}/download-url")
  @Operation(summary = "URL temporária de conteúdo aprovado")
  public ResponseEntity<AssetService.DownloadLink> link(
      @PathVariable UUID bookId, @PathVariable UUID assetId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.link(bookId, assetId, true));
  }

  @GetMapping("/chapters")
  @Operation(summary = "Listar capítulos reader-ready")
  public List<?> chapters(@PathVariable UUID bookId) {
    return jdbc.queryForList("SELECT id, edition_id, text_asset_version_id, chapter_key, parent_chapter_id, position, title, start_offset, end_offset, confidence FROM catalog.book_chapter WHERE book_id=? ORDER BY position, start_offset", bookId);
  }

  /**
   * Resolves the signed URL for the exact normalized text version used by a
   * chapter. This is an internal service boundary; the content service proxies
   * the bytes and never exposes storage credentials or permanent object URLs.
   */
  @GetMapping("/text-versions/{versionId}/download-url")
  @Operation(summary = "Gerar URL interna da versão textual exata")
  public ResponseEntity<AssetService.DownloadLink> textVersionLink(
      @PathVariable UUID bookId, @PathVariable UUID versionId) {
    UUID assetId = jdbc.queryForObject(
        "SELECT v.book_asset_id FROM catalog.book_asset_version v "
            + "JOIN catalog.book_asset a ON a.id=v.book_asset_id "
            + "WHERE v.id=? AND a.book_id=? AND a.asset_type='TXT'",
        UUID.class, versionId, bookId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.linkVersion(bookId, versionId));
  }

  @GetMapping(
      value = "/text-versions/{versionId}/content", produces = MediaType.TEXT_PLAIN_VALUE)
  @Operation(summary = "Ler a versão textual exata para o content-service")
  public ResponseEntity<Resource> textVersionContent(
      @PathVariable UUID bookId, @PathVariable UUID versionId) {
    InputStream stream = service.downloadVersion(bookId, versionId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.TEXT_PLAIN)
        .body(new InputStreamResource(stream));
  }
}
