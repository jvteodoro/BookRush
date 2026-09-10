package com.bookrush.catalog.processing;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Internal lineage boundary used by ingestion workers after storage confirmation. */
@RestController
@RequestMapping("/api/internal/v1/catalog/processings")
@Tag(name = "Processamento interno", description = "Registra a linhagem entre versões de assets após uma transformação.")
@SecurityRequirement(name = "catalogService")
public class ProcessingController {
  private final JdbcTemplate jdbc;
  public ProcessingController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Request(UUID inputVersionId, List<UUID> outputVersionIds, String processingType,
      String processor, String processorVersion, Map<String,Object> metadata) {}

  public record ChapterRequest(UUID bookId, UUID editionId, UUID textAssetVersionId, List<Chapter> chapters) {}
  public record Chapter(String key, String parent, int position, String title, int start, int end, String confidence) {}

  @PostMapping
  @Operation(summary = "Registrar processamento", description = "Registra um input e um ou mais outputs, preservando hashes e metadata do processador para auditoria.")
  public Map<String,Object> create(@RequestBody Request request) {
    if (request.inputVersionId() == null || request.outputVersionIds() == null || request.outputVersionIds().isEmpty())
      throw new IllegalArgumentException("input and outputs are required");
    UUID id = UUID.randomUUID();
    UUID output = request.outputVersionIds().getFirst();
    jdbc.update("INSERT INTO catalog.asset_processing(id,input_asset_version_id,output_asset_version_id,processing_type,processor,processor_version,status,started_at,finished_at,metadata) VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', clock_timestamp(), clock_timestamp(), ?::jsonb)", id, request.inputVersionId(), output, request.processingType(), request.processor(), request.processorVersion(), new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(request.metadata() == null ? Map.of() : request.metadata()).toString());
    for (int i=0; i<request.outputVersionIds().size(); i++) {
      UUID version = request.outputVersionIds().get(i);
      String hash = jdbc.queryForObject("SELECT sha256 FROM catalog.book_asset_version WHERE id=?", String.class, version);
      if (i == 0) jdbc.update("INSERT INTO catalog.processing_output(processing_id,output_order,asset_version_id,sha256) VALUES (?, ?, ?, ?)", id, i, version, hash);
      else jdbc.update("INSERT INTO catalog.processing_output(processing_id,output_order,asset_version_id,sha256) VALUES (?, ?, ?, ?)", id, i, version, hash);
    }
    String inputHash = jdbc.queryForObject("SELECT sha256 FROM catalog.book_asset_version WHERE id=?", String.class, request.inputVersionId());
    jdbc.update("INSERT INTO catalog.processing_input(processing_id,input_order,asset_version_id,sha256) VALUES (?, 0, ?, ?)", id, request.inputVersionId(), inputHash);
    return Map.of("id", id, "status", "SUCCEEDED");
  }

  @PostMapping("/chapters")
  @Transactional
  @Operation(summary = "Projetar capítulos", description = "Materializa chapters.json no PostgreSQL de forma idempotente para a versão de texto informada.")
  public Map<String,Object> chapters(@RequestBody ChapterRequest request) {
    if (request.bookId() == null || request.editionId() == null || request.textAssetVersionId() == null || request.chapters() == null || request.chapters().isEmpty())
      throw new IllegalArgumentException("book, edition, text version and chapters are required");
    jdbc.update("DELETE FROM catalog.book_chapter WHERE text_asset_version_id=?", request.textAssetVersionId());
    var ids = new HashMap<String, UUID>();
    for (var chapter : request.chapters()) {
      var id = UUID.randomUUID();
      var parentId = chapter.parent() == null ? null : ids.get(chapter.parent());
      jdbc.update("INSERT INTO catalog.book_chapter(id, book_id, edition_id, text_asset_version_id, chapter_key, parent_chapter_id, position, title, start_offset, end_offset, confidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, request.bookId(), request.editionId(), request.textAssetVersionId(), chapter.key(), parentId, chapter.position(), chapter.title(), chapter.start(), chapter.end(), "EXPLICIT".equals(chapter.confidence()) ? "EXPLICIT" : "FALLBACK");
      ids.put(chapter.key(), id);
    }
    return Map.of("textAssetVersionId", request.textAssetVersionId(), "chapters", ids.size(), "status", "PROJECTED");
  }
}
