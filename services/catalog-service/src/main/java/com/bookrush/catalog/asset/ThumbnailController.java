package com.bookrush.catalog.asset;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

/** Public redirect to a short-lived signed URL for an approved book thumbnail. */
@RestController
@ConditionalOnProperty(name = "storage.enabled", havingValue = "true")
public class ThumbnailController {
  private final AssetService assets;
  private final JdbcTemplate jdbc;

  public ThumbnailController(AssetService assets, JdbcTemplate jdbc) {
    this.assets = assets;
    this.jdbc = jdbc;
  }

  @GetMapping("/api/v1/books/{bookId}/thumbnail")
  public ResponseEntity<Void> redirect(@PathVariable UUID bookId) {
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
    var link = assets.link(bookId, assetId, true);
    return ResponseEntity.status(302)
        .location(link.url())
        .cacheControl(CacheControl.noCache())
        .build();
  }
}
