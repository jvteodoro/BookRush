package com.bookrush.catalog.asset;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

/** Serves an approved thumbnail through the protected reader route. */
@RestController
@ConditionalOnProperty(name = "storage.enabled", havingValue = "true")
public class ThumbnailController {
  private final AssetService assets;
  private final JdbcTemplate jdbc;

  @org.springframework.beans.factory.annotation.Value("${bookrush.security.product-access.enabled:false}")
  private boolean protectedDistribution;

  public ThumbnailController(AssetService assets, JdbcTemplate jdbc) {
    this.assets = assets;
    this.jdbc = jdbc;
  }

  @GetMapping("/api/v1/books/{bookId}/thumbnail")
  public ResponseEntity<?> redirect(@PathVariable UUID bookId, jakarta.servlet.http.HttpServletRequest request) {
    UUID assetId =
        jdbc.query(
                "SELECT a.id FROM catalog.book_asset a WHERE a.book_id=? AND a.asset_type='THUMBNAIL'"
                    + " AND a.asset_role='COVER' AND a.status='ACTIVE' ORDER BY a.updated_at DESC",
                (rs, row) -> rs.getObject("id", UUID.class),
                bookId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Thumbnail not found"));
    if (protectedDistribution) {
      return ReaderContentController.readerResponse(assets, assets.readerDownload(bookId, assetId, true), request);
    }
    var link = assets.thumbnailLink(bookId, assetId);
    return ResponseEntity.status(302)
        .location(link.url())
        .cacheControl(CacheControl.noCache())
        .build();
  }
}
