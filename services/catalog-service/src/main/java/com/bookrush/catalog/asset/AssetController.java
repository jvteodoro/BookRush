package com.bookrush.catalog.asset;

import com.bookrush.catalog.persistence.model.*;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnProperty(name="storage.enabled",havingValue="true")
@RequestMapping("/api/admin/books/{bookId}/assets")
public class AssetController {
  private final AssetService service;
  public AssetController(AssetService service) {this.service=service;}
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AssetService.UploadResult upload(@PathVariable UUID bookId,@RequestParam UUID sourceId,
      @RequestParam(required=false) UUID editionId,@RequestParam(required=false) UUID licenseId,
      @RequestParam BookAssetAssetType type,@RequestParam BookAssetAssetRole role,@RequestParam MultipartFile file) throws IOException {
    try(var in=file.getInputStream()) {
      return service.upload(bookId,null,editionId,sourceId,licenseId,type,role,file.getOriginalFilename(),file.getContentType(),in);
    }
  }
  @PostMapping(value="/{assetId}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AssetService.UploadResult version(@PathVariable UUID bookId,@PathVariable UUID assetId,
      @RequestParam MultipartFile file) throws IOException {
    var a=service.get(bookId,assetId);
    try(var in=file.getInputStream()) {
      return service.upload(bookId,assetId,null,null,null,a.type(),a.role(),file.getOriginalFilename(),file.getContentType(),in);
    }
  }
  @GetMapping public List<AssetService.AssetView> list(@PathVariable UUID bookId,@RequestParam(defaultValue="0") int page) {return service.list(bookId,page);}
  @GetMapping("/{assetId}") public AssetService.AssetView get(@PathVariable UUID bookId,@PathVariable UUID assetId) {return service.get(bookId,assetId);}
  @GetMapping("/{assetId}/versions") public List<AssetService.VersionView> versions(@PathVariable UUID bookId,@PathVariable UUID assetId,@RequestParam(defaultValue="0") int page) {return service.versions(bookId,assetId,page);}
  @PostMapping("/{assetId}/approve") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void approve(@PathVariable UUID bookId,@PathVariable UUID assetId) {service.approve(bookId,assetId);}
  @GetMapping("/{assetId}/download-url")
  public ResponseEntity<AssetService.DownloadLink> link(@PathVariable UUID bookId,@PathVariable UUID assetId) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.link(bookId,assetId,false));}
  @DeleteMapping("/{assetId}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID bookId,@PathVariable UUID assetId) {service.delete(bookId,assetId);}
}
