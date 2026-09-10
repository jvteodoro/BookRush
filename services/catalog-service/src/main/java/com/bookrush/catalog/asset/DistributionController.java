package com.bookrush.catalog.asset;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@ConditionalOnProperty(name="storage.enabled",havingValue="true")
@Tag(name = "Distribuição", description = "Acesso de leitura a assets aprovados por URL pré-assinada.")
public class DistributionController {
  private final AssetService service;
  public DistributionController(AssetService service) {this.service=service;}
  @GetMapping("/api/books/{bookId}/assets/{assetId}/download-url")
  @Operation(summary = "Gerar URL pública temporária", description = "Retorna uma URL pré-assinada somente quando o asset está aprovado. O arquivo é baixado diretamente do object storage.")
  public ResponseEntity<AssetService.DownloadLink> download(@PathVariable UUID bookId,@PathVariable UUID assetId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.link(bookId,assetId,true));
  }
}
