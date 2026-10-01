package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.source.GutenbergCatalogClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/ingestion/gutenberg")
@Tag(
    name = "Catálogo Gutenberg",
    description = "Consulta paginada do índice público para selecionar IDs antes da ingestão.")
@SecurityRequirement(name = "keycloak")
public class GutenbergCatalogController {
  private final GutenbergCatalogClient catalog;
  private final IngestionJobService jobs;

  public GutenbergCatalogController(GutenbergCatalogClient catalog, IngestionJobService jobs) {
    this.catalog = catalog;
    this.jobs = jobs;
  }

  @GetMapping("/catalog")
  @Operation(
      summary = "Listar livros Gutenberg",
      description =
          "Consulta somente o índice paginado; assets só são baixados após POST /ingestion/run.")
  public GutenbergCatalogClient.Page page(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return catalog.page(q, page, size);
  }

  @GetMapping("/catalog/status")
  @Operation(
      summary = "Consultar status de IDs Gutenberg",
      description = "Consulta o último estado persistido de cada ID, sem baixar assets.")
  public Map<String, Map<String, Object>> status(@RequestParam String ids) {
    return jobs.getExternalStatuses(Arrays.stream(ids.split(",")).map(String::trim).toList());
  }
}
