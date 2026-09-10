package com.bookrush.catalog.asset;

import com.bookrush.catalog.persistence.model.*;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@ConditionalOnProperty(name="storage.enabled",havingValue="true")
@RequestMapping("/api/admin/books/{bookId}/assets")
@Tag(name = "Assets administrativos", description = "Ingestão, versionamento, aprovação e remoção de arquivos associados a livros. Os binários são enviados ao object storage; o PostgreSQL guarda apenas metadata.")
@SecurityRequirement(name = "assetAdmin")
public class AssetController {
  private final AssetService service;
  public AssetController(AssetService service) {this.service=service;}
  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Enviar um novo asset", description = "Cria um asset lógico e sua primeira versão física. O arquivo é validado, recebe hash SHA-256 e é persistido no bucket definido pelo papel.")
  @ResponseStatus(HttpStatus.CREATED)
  public AssetService.UploadResult upload(@PathVariable UUID bookId,@RequestParam UUID sourceId,
      @RequestParam(required=false) UUID editionId,@RequestParam(required=false) UUID licenseId,
      @RequestParam BookAssetAssetType type,@RequestParam BookAssetAssetRole role,@RequestParam MultipartFile file) throws IOException {
    try(var in=file.getInputStream()) {
      return service.upload(bookId,null,editionId,sourceId,licenseId,type,role,file.getOriginalFilename(),file.getContentType(),in);
    }
  }
  @PostMapping(value="/{assetId}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Criar nova versão de asset", description = "Adiciona uma versão ao asset existente. Assets SOURCE não podem ser sobrescritos; use esta operação apenas para versões permitidas pelo domínio.")
  @ResponseStatus(HttpStatus.CREATED)
  public AssetService.UploadResult version(@PathVariable UUID bookId,@PathVariable UUID assetId,
      @RequestParam MultipartFile file) throws IOException {
    var a=service.get(bookId,assetId);
    try(var in=file.getInputStream()) {
      return service.upload(bookId,assetId,null,null,null,a.type(),a.role(),file.getOriginalFilename(),file.getContentType(),in);
    }
  }
  @GetMapping @Operation(summary = "Listar assets de um livro", description = "Lista assets paginados, incluindo tipo, papel, estado e referência à versão atual.")
  public List<AssetService.AssetView> list(@PathVariable UUID bookId,@RequestParam(defaultValue="0") int page) {return service.list(bookId,page);}
  @GetMapping("/{assetId}") @Operation(summary = "Consultar um asset", description = "Retorna a metadata do asset sem transferir o binário.")
  public AssetService.AssetView get(@PathVariable UUID bookId,@PathVariable UUID assetId) {return service.get(bookId,assetId);}
  @GetMapping("/{assetId}/versions") @Operation(summary = "Listar versões", description = "Lista todas as versões físicas de um asset, incluindo hash, bucket, object key e estado.")
  public List<AssetService.VersionView> versions(@PathVariable UUID bookId,@PathVariable UUID assetId,@RequestParam(defaultValue="0") int page) {return service.versions(bookId,assetId,page);}
  @PostMapping("/{assetId}/approve") @Operation(summary = "Aprovar asset", description = "Marca o asset como disponível para distribuição após revisão administrativa.") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void approve(@PathVariable UUID bookId,@PathVariable UUID assetId) {service.approve(bookId,assetId);}
  @GetMapping("/{assetId}/download-url")
  @Operation(summary = "Gerar URL administrativa de download", description = "Retorna uma URL pré-assinada temporária para inspeção do arquivo. Não faz proxy do conteúdo pelo catálogo.")
  public ResponseEntity<AssetService.DownloadLink> link(@PathVariable UUID bookId,@PathVariable UUID assetId) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.link(bookId,assetId,false));}
  @DeleteMapping("/{assetId}") @Operation(summary = "Excluir asset", description = "Marca o asset como excluído e agenda/realiza a remoção física conforme a política de storage.") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID bookId,@PathVariable UUID assetId) {service.delete(bookId,assetId);}
}
