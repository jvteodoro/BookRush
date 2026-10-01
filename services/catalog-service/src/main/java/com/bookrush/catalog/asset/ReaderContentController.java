package com.bookrush.catalog.asset;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="storage.enabled", havingValue="true")
@RequestMapping("/api/v1/books/{bookId}/reader-assets")
public class ReaderContentController {
  private final AssetService service;
  public ReaderContentController(AssetService service){this.service=service;}
  @GetMapping @Operation(summary="Listar assets reader-ready")
  public List<AssetService.AssetView> list(@PathVariable UUID bookId){return service.list(bookId,0).stream().filter(a -> a.status().name().equals("ACTIVE")).toList();}
  @GetMapping("/{assetId}/download-url") @Operation(summary="URL temporária de conteúdo aprovado")
  public ResponseEntity<AssetService.DownloadLink> link(@PathVariable UUID bookId,@PathVariable UUID assetId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.link(bookId,assetId,true));}
}
