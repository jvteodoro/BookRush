package com.bookrush.catalog.asset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "storage.enabled", havingValue = "true")
@Tag(
    name = "Distribuição",
    description = "Entrega de assets aprovados.")
public class DistributionController {
  private final AssetService service;

  @org.springframework.beans.factory.annotation.Value("${bookrush.security.product-access.enabled:false}")
  private boolean protectedDistribution;

  public DistributionController(AssetService service) {
    this.service = service;
  }

  @GetMapping("/api/books/{bookId}/assets/{assetId}/download-url")
  @Operation(
      summary = "Obter link de download",
      description =
          "No modo protegido, retorna a rota autenticada do asset aprovado.")
  public ResponseEntity<AssetService.DownloadLink> download(
      @PathVariable UUID bookId, @PathVariable UUID assetId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(protectedDistribution ? service.authenticatedLink(bookId, assetId) : service.link(bookId, assetId, true));
  }
}
