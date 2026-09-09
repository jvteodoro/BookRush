package com.bookrush.catalog.asset;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="storage.enabled",havingValue="true")
public class DistributionController {
  private final AssetService service;
  public DistributionController(AssetService service) {this.service=service;}
  @GetMapping("/api/books/{bookId}/assets/{assetId}/download-url")
  public ResponseEntity<AssetService.DownloadLink> download(@PathVariable UUID bookId,@PathVariable UUID assetId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.link(bookId,assetId,true));
  }
}
