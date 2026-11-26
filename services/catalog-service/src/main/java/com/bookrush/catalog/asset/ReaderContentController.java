package com.bookrush.catalog.asset;

import io.swagger.v3.oas.annotations.Operation;
import java.io.InputStream;
import jakarta.servlet.http.HttpServletRequest;
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

  @org.springframework.beans.factory.annotation.Value("${bookrush.security.product-access.enabled:false}")
  private boolean protectedDistribution;
  public ReaderContentController(AssetService service, JdbcTemplate jdbc) {
    this.service = service;
    this.jdbc = jdbc;
  }
  @GetMapping
  @Operation(summary = "Listar assets reader-ready")
  public List<AssetService.AssetView> list(@PathVariable UUID bookId) {
    if (protectedDistribution) return service.approvedReaderAssets(bookId);
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
        .body(protectedDistribution ? service.authenticatedLink(bookId, assetId) : service.link(bookId, assetId, true));
  }

  @GetMapping("/chapters")
  @Operation(summary = "Listar capítulos reader-ready")
  public List<?> chapters(@PathVariable UUID bookId) {
    var chapters = jdbc.queryForList(
        "SELECT c.id, c.edition_id, c.text_asset_version_id, c.chapter_key, "
            + "c.parent_chapter_id, c.position, c.title, c.start_offset, c.end_offset, c.confidence "
            + "FROM catalog.book_chapter c "
            + "WHERE c.book_id=? AND c.text_asset_version_id=("
            + "SELECT v.id FROM catalog.book_asset_version v "
            + "JOIN catalog.book_asset a ON a.id=v.book_asset_id "
            + "WHERE a.book_id=c.book_id AND a.asset_type='TXT' "
            + "AND v.status='AVAILABLE' "
            + (protectedDistribution ? "AND a.status<>'DELETED' ORDER BY v.created_at DESC, v.id DESC LIMIT 1) "
                : "ORDER BY v.created_at DESC LIMIT 1) ")
            + "ORDER BY c.position, c.start_offset",
        bookId);
    if (protectedDistribution && !chapters.isEmpty()) {
      service.readerText(bookId, (UUID) chapters.getFirst().get("text_asset_version_id"));
    }
    return chapters;
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
    if (protectedDistribution) {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .body(service.authenticatedTextLink(bookId, versionId));
    }
    jdbc.queryForObject(
        "SELECT v.book_asset_id FROM catalog.book_asset_version v "
            + "JOIN catalog.book_asset a ON a.id=v.book_asset_id "
            + "WHERE v.id=? AND a.book_id=? AND a.asset_type='TXT'",
        UUID.class, versionId, bookId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.linkVersion(bookId, versionId));
  }

  @GetMapping(
      value = "/text-versions/{versionId}/content", produces = "text/plain;charset=UTF-8")
  @Operation(summary = "Ler a versão textual exata para o content-service")
  public ResponseEntity<Resource> textVersionContent(
      @PathVariable UUID bookId, @PathVariable UUID versionId, HttpServletRequest request) {
    if (protectedDistribution) {
      var download = service.readerText(bookId, versionId);
      return readerResponse(service, new AssetService.ReaderDownload(download.location(),
          "text/plain;charset=UTF-8", download.size()), request);
    }
    InputStream stream = service.downloadVersion(bookId, versionId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
        .body(new InputStreamResource(stream));
  }

  @GetMapping("/{assetId}/content")
  @Operation(summary = "Ler asset EPUB para o content-service")
  public ResponseEntity<Resource> assetContent(
      @PathVariable UUID bookId, @PathVariable UUID assetId, HttpServletRequest request) {
    if (protectedDistribution) return readerResponse(service, service.readerDownload(bookId, assetId, false), request);
    InputStream stream = service.downloadLatest(bookId, assetId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new InputStreamResource(stream));
  }

  static ResponseEntity<Resource> readerResponse(AssetService service, AssetService.ReaderDownload download,
      HttpServletRequest request) {
    var response = ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
        .header("Accept-Ranges", "none").header("X-Content-Type-Options", "nosniff")
        .contentType(download.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(download.contentType()));
    if (download.size() != null) response.contentLength(download.size());
    return "HEAD".equals(request.getMethod()) ? response.build()
        : response.body(new InputStreamResource(service.readerBytes(download)));
  }
}
