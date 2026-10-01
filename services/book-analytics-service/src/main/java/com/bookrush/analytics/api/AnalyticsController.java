package com.bookrush.analytics.api;

import com.bookrush.analytics.observability.AnalyticsMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Analytics", description = "Execuções e consultas internas de analytics")
public class AnalyticsController {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final AnalyticsMetrics metrics;

  public AnalyticsController(JdbcTemplate jdbc, ObjectMapper mapper, AnalyticsMetrics metrics) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.metrics = metrics;
  }

  public record JobRequest(
      @NotBlank String operationKey,
      @NotEmpty java.util.List<UUID> inputAssetVersionIds,
      Map<String, Object> configuration) {}

  @PostMapping({"/admin/v1/analytics/jobs", "/admin/v1/content-analytics/jobs"})
  @Transactional
  @Operation(
      summary = "Criar job de analytics",
      description =
          "Cria ou recupera um job pela operation key; replay com outra entrada é rejeitado.")
  public ResponseEntity<Map<String, Object>> createJob(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody JobRequest request) {
    var key =
        idempotencyKey == null || idempotencyKey.isBlank()
            ? request.operationKey()
            : idempotencyKey.trim();
    if (!key.equals(request.operationKey()))
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "code",
                  "IDEMPOTENCY_KEY_MISMATCH",
                  "message",
                  "Idempotency-Key must equal operationKey"));
    var requestHash = hash(request);
    var existing =
        jdbc.query(
            "SELECT id, status, total_items, request_hash FROM analytics.analysis_job WHERE"
                + " operation_key=?",
            (rs, n) ->
                new ExistingJob(
                    rs.getObject("id", UUID.class),
                    rs.getString("status"),
                    rs.getInt("total_items"),
                    rs.getString("request_hash")),
            key);
    if (!existing.isEmpty()) {
      var row = existing.getFirst();
      if (!requestHash.equals(row.requestHash())) {
        metrics.jobConflict();
        return ResponseEntity.status(409)
            .body(
                Map.of(
                    "code",
                    "IDEMPOTENCY_CONFLICT",
                    "message",
                    "operationKey already exists with a different request"));
      }
      metrics.jobReplayed();
      var body =
          Map.<String, Object>of(
              "id", row.id(), "status", row.status(), "totalItems", row.totalItems());
      return ResponseEntity.ok()
          .header(HttpHeaders.LOCATION, "/api/admin/v1/analytics/jobs/" + row.id())
          .body(body);
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " analytics.analysis_job(id,operation_key,request_hash,status,configuration,total_items)"
            + " VALUES (?,?,?,'PENDING',?::jsonb,?)",
        id,
        key,
        requestHash,
        mapper
            .valueToTree(request.configuration() == null ? Map.of() : request.configuration())
            .toString(),
        request.inputAssetVersionIds().size());
    for (UUID version : request.inputAssetVersionIds()) {
      jdbc.update(
          "INSERT INTO analytics.analysis_job_item(id,job_id,input_asset_version_id,status) VALUES"
              + " (?,?,?,'PENDING')",
          UUID.randomUUID(),
          id,
          version);
    }
    metrics.jobCreated();
    return ResponseEntity.accepted()
        .header(HttpHeaders.LOCATION, "/api/admin/v1/analytics/jobs/" + id)
        .body(
            Map.of(
                "id",
                id,
                "status",
                "PENDING",
                "totalItems",
                request.inputAssetVersionIds().size()));
  }

  private String hash(JobRequest request) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request)));
    } catch (Exception e) {
      throw new IllegalStateException("cannot hash analytics request", e);
    }
  }

  private record ExistingJob(UUID id, String status, int totalItems, String requestHash) {}

  @GetMapping({"/admin/v1/analytics/jobs/{jobId}", "/admin/v1/content-analytics/jobs/{jobId}"})
  @Operation(summary = "Consultar job", description = "Consulta estado e contadores persistidos.")
  public ResponseEntity<Map<String, Object>> job(@PathVariable UUID jobId) {
    var rows =
        jdbc.queryForList(
            "SELECT id, operation_key, status, total_items, processed_items, succeeded_items,"
                + " failed_items, cancel_requested, created_at, started_at, finished_at FROM"
                + " analytics.analysis_job WHERE id=?",
            jobId);
    return rows.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(rows.getFirst());
  }

  @PostMapping({
    "/admin/v1/analytics/jobs/{jobId}/cancel",
    "/admin/v1/content-analytics/jobs/{jobId}/cancel"
  })
  @Operation(
      summary = "Solicitar cancelamento",
      description = "Marca cancelamento cooperativo para o worker persistente.")
  public ResponseEntity<Void> cancel(@PathVariable UUID jobId) {
    int updated =
        jdbc.update(
            "UPDATE analytics.analysis_job SET cancel_requested=true WHERE id=? AND status IN"
                + " ('PENDING','RUNNING')",
            jobId);
    return updated == 0 ? ResponseEntity.notFound().build() : ResponseEntity.accepted().build();
  }

  @PostMapping("/admin/v1/analytics/jobs/{jobId}/resume")
  @Operation(
      summary = "Retomar job",
      description = "Recoloca itens falhos ou cancelados na fila sem apagar tentativas históricas.")
  public ResponseEntity<Void> resume(@PathVariable UUID jobId) {
    int updated =
        jdbc.update(
            "UPDATE analytics.analysis_job SET status='PENDING', cancel_requested=false,"
                + " finished_at=NULL WHERE id=? AND status IN"
                + " ('FAILED','CANCELLED','COMPLETED_WITH_ERRORS')",
            jobId);
    if (updated == 0) return ResponseEntity.notFound().build();
    jdbc.update(
        "UPDATE analytics.analysis_job_item SET status='PENDING', finished_at=NULL,"
            + " error_code=NULL, error_message=NULL WHERE job_id=? AND status IN"
            + " ('FAILED','CANCELLED')",
        jobId);
    return ResponseEntity.accepted().build();
  }

  @GetMapping({
    "/admin/v1/analytics/jobs/{jobId}/items",
    "/admin/v1/content-analytics/jobs/{jobId}/items"
  })
  @Operation(
      summary = "Listar itens do job",
      description = "Consulta itens, tentativas e erros sem perder histórico do job.")
  public ResponseEntity<Object> items(@PathVariable UUID jobId) {
    var exists =
        jdbc.queryForObject(
            "SELECT count(*) FROM analytics.analysis_job WHERE id=?", Integer.class, jobId);
    if (exists == null || exists == 0) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(
        jdbc.queryForList(
            "SELECT id, input_asset_version_id, status, attempt_count, analysis_run_id, error_code,"
                + " error_message, started_at, finished_at FROM analytics.analysis_job_item WHERE"
                + " job_id=? ORDER BY created_at, id",
            jobId));
  }

  @GetMapping({"/internal/v1/analytics/runs", "/internal/v1/content-analytics/runs"})
  @Operation(
      summary = "Listar execuções e métricas",
      description = "Retorna as execuções persistidas, associadas à obra e à versão textual exata.")
  public ResponseEntity<Map<String, Object>> runs(@RequestParam(defaultValue = "100") int limit) {
    if (limit < 1 || limit > 200)
      return ResponseEntity.badRequest()
          .body(Map.of("code", "INVALID_LIMIT", "message", "limit must be between 1 and 200"));
    var rows =
        jdbc.queryForList(
            """
SELECT r.id, a.book_id, r.input_asset_version_id, r.status,
       COALESCE(an.model_version, an.code) AS model_version,
       r.started_at, r.finished_at, r.created_at,
       (SELECT count(*) FROM analytics.excerpt e WHERE e.source_asset_version_id=r.input_asset_version_id) AS excerpt_count
  FROM analytics.analysis_run r
  JOIN catalog.book_asset_version v ON v.id=r.input_asset_version_id
  JOIN catalog.book_asset a ON a.id=v.book_asset_id
  JOIN analytics.analyzer an ON an.id=r.analyzer_id
 ORDER BY r.created_at DESC, r.id DESC
 LIMIT ?
""",
            limit);
    for (var row : rows) {
      var runId = row.get("id");
      var metricsRows =
          jdbc.queryForList(
              """
              SELECT fd.code, fd.name, fd.unit, df.numeric_value, df.value_status
                FROM analytics.document_feature df
                JOIN analytics.feature_definition fd ON fd.id=df.feature_definition_id
               WHERE df.analysis_run_id=? AND df.numeric_value IS NOT NULL
               ORDER BY fd.code
               LIMIT 40
              """,
              runId);
      var metricsList = new java.util.ArrayList<Map<String, Object>>();
      var excerptCount = ((Number) row.getOrDefault("excerpt_count", 0)).doubleValue();
      metricsList.add(
          Map.of(
              "key", "excerpt_count", "label", "Excerpts", "value", excerptCount, "unit", "itens"));
      var metricKeys = new java.util.HashSet<String>();
      for (var metric : metricsRows) {
        var valueStatus = String.valueOf(metric.getOrDefault("value_status", "VALID"));
        if (!"VALID".equals(valueStatus)) continue;
        var value = metric.get("numeric_value");
        if (!(value instanceof Number number)) continue;
        var key = String.valueOf(metric.get("code"));
        metricKeys.add(key);
        var metricMap = new LinkedHashMap<String, Object>();
        metricMap.put("key", key);
        metricMap.put("label", metric.getOrDefault("name", key));
        metricMap.put("value", number.doubleValue());
        if (metric.get("unit") != null) metricMap.put("unit", metric.get("unit"));
        metricsList.add(metricMap);
      }
      var excerptMetrics =
          jdbc.queryForList(
              """
SELECT fd.code, fd.name, fd.unit, avg(ef.numeric_value) AS numeric_value
  FROM analytics.excerpt_feature ef
  JOIN analytics.feature_definition fd ON fd.id=ef.feature_definition_id
 WHERE ef.analysis_run_id=? AND ef.numeric_value IS NOT NULL AND ef.value_status='VALID'
 GROUP BY fd.code, fd.name, fd.unit
 ORDER BY fd.code
 LIMIT 40
""",
              runId);
      for (var metric : excerptMetrics) {
        var key = String.valueOf(metric.get("code"));
        if (metricKeys.contains(key) || !(metric.get("numeric_value") instanceof Number number))
          continue;
        var metricMap = new LinkedHashMap<String, Object>();
        metricMap.put("key", key);
        metricMap.put("label", metric.getOrDefault("name", key) + " (média)");
        metricMap.put("value", number.doubleValue());
        if (metric.get("unit") != null) metricMap.put("unit", metric.get("unit"));
        metricsList.add(metricMap);
      }
      ((Map<String, Object>) row).put("metrics", metricsList);
    }
    return ResponseEntity.ok(Map.of("items", rows, "total", rows.size()));
  }

  @GetMapping({
    "/internal/v1/analytics/books/{bookId}/input",
    "/internal/v1/content-analytics/books/{bookId}/input"
  })
  @Operation(
      summary = "Resolver texto para analytics",
      description =
          "Lista versões TXT disponíveis sem conceder acesso administrativo aos assets do"
              + " catálogo.")
  public ResponseEntity<Map<String, Object>> inputVersions(@PathVariable UUID bookId) {
    var items =
        jdbc.queryForList(
            """
SELECT a.id AS asset_id, a.asset_type, a.asset_role,
       v.id AS version_id, v.version_number, v.status, v.size_bytes, v.sha256
  FROM catalog.book_asset a
  JOIN catalog.book_asset_version v ON v.book_asset_id=a.id
 WHERE a.book_id=? AND a.status<>'DELETED' AND a.asset_type='TXT'
   AND a.asset_role IN ('NORMALIZED','PROCESSING','ANALYTICS')
   AND v.status='AVAILABLE' AND v.availability_status='AVAILABLE'
 ORDER BY CASE a.asset_role WHEN 'NORMALIZED' THEN 0 WHEN 'PROCESSING' THEN 1 ELSE 2 END,
          v.version_number DESC
""",
            bookId);
    return ResponseEntity.ok(Map.of("bookId", bookId, "items", items));
  }

  @GetMapping({
    "/internal/v1/analytics/books/{bookId}/summary",
    "/internal/v1/content-analytics/books/{bookId}"
  })
  @Operation(
      summary = "Resumo de analytics",
      description = "Retorna contagens por versão textual sem misturar versões do catálogo.")
  public ResponseEntity<Map<String, Object>> summary(@PathVariable UUID bookId) {
    var row =
        jdbc.queryForMap(
            """
            SELECT count(*) AS excerpts,
                   count(DISTINCT e.source_asset_version_id) AS text_versions
              FROM analytics.excerpt e
              JOIN catalog.book_asset_version v ON v.id = e.source_asset_version_id
              JOIN catalog.book_asset a ON a.id = v.book_asset_id
             WHERE a.book_id = ?
            """,
            bookId);
    return ResponseEntity.ok(
        Map.of(
            "bookId",
            bookId,
            "excerpts",
            row.get("excerpts"),
            "textVersions",
            row.get("text_versions")));
  }

  @GetMapping({
    "/internal/v1/analytics/books/{bookId}/excerpts",
    "/internal/v1/content-analytics/books/{bookId}/excerpts"
  })
  @Operation(
      summary = "Listar excerpts",
      description = "Lista excerpts sem atravessar a versão textual de origem.")
  public ResponseEntity<Map<String, Object>> excerpts(
      @PathVariable UUID bookId,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    if (offset < 0 || limit < 1 || limit > 200)
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "code",
                  "INVALID_PAGE",
                  "message",
                  "limit must be 1..200 and offset non-negative"));
    var rows =
        jdbc.queryForList(
            """
SELECT e.id, e.source_asset_version_id, e.chapter_id, e.start_codepoint,
       e.end_codepoint, e.text, e.text_sha256, e.generation_method,
       e.generator_version, e.created_at,
       er.final_score AS candidate_score, er.ranker_code, er.ranker_version,
       em.code AS embedding_model, em.model_version AS embedding_model_version
  FROM analytics.excerpt e
  JOIN catalog.book_asset_version v ON v.id = e.source_asset_version_id
  JOIN catalog.book_asset a ON a.id = v.book_asset_id
  LEFT JOIN LATERAL (SELECT ee.embedding_model_id FROM analytics.excerpt_embedding ee
                      WHERE ee.excerpt_id = e.id ORDER BY ee.created_at DESC LIMIT 1) ee ON TRUE
 LEFT JOIN analytics.embedding_model em ON em.id = ee.embedding_model_id
 LEFT JOIN LATERAL (SELECT final_score, ranker_code, ranker_version FROM analytics.excerpt_rank er
                    WHERE er.excerpt_id=e.id ORDER BY er.created_at DESC LIMIT 1) er ON TRUE
 WHERE a.book_id = ? ORDER BY e.created_at, e.start_codepoint
 LIMIT ? OFFSET ?
""",
            bookId,
            limit,
            offset);
    metrics.excerptsListed(rows.size());
    return ResponseEntity.ok(
        Map.of("bookId", bookId, "offset", offset, "limit", limit, "items", rows));
  }

  @GetMapping("/internal/v1/content-analytics/asset-versions/{assetVersionId}")
  @Operation(
      summary = "Consultar analytics de uma versão textual",
      description = "Consulta somente observações e excerpts ligados à versão física exata.")
  public ResponseEntity<Map<String, Object>> assetVersion(@PathVariable UUID assetVersionId) {
    var exists =
        jdbc.queryForObject(
            "SELECT count(*) FROM catalog.book_asset_version WHERE id=?",
            Integer.class,
            assetVersionId);
    if (exists == null || exists == 0) return ResponseEntity.notFound().build();
    var excerpts =
        jdbc.queryForList(
            "SELECT id, chapter_id, start_codepoint, end_codepoint, text_sha256, word_count,"
                + " sentence_count, generation_method, generator_version FROM analytics.excerpt"
                + " WHERE source_asset_version_id=? ORDER BY start_codepoint",
            assetVersionId);
    return ResponseEntity.ok(Map.of("assetVersionId", assetVersionId, "excerpts", excerpts));
  }

  @GetMapping("/internal/v1/content-analytics/asset-versions/{assetVersionId}/excerpts")
  @Operation(
      summary = "Listar excerpts de uma versão textual",
      description = "Os offsets são code points Unicode e o intervalo é half-open.")
  public ResponseEntity<Object> assetVersionExcerpts(@PathVariable UUID assetVersionId) {
    var rows =
        jdbc.queryForList(
            "SELECT id, chapter_id, start_codepoint, end_codepoint, text, text_sha256, word_count,"
                + " sentence_count, generation_method, generator_version, created_at FROM"
                + " analytics.excerpt WHERE source_asset_version_id=? ORDER BY start_codepoint",
            assetVersionId);
    return ResponseEntity.ok(Map.of("assetVersionId", assetVersionId, "items", rows));
  }

  @GetMapping("/internal/v1/content-analytics/excerpts/{excerptId}")
  @Operation(
      summary = "Consultar excerpt",
      description = "Retorna o hash e a versão textual de origem para verificação de drift.")
  public ResponseEntity<Map<String, Object>> excerpt(@PathVariable UUID excerptId) {
    var rows =
        jdbc.queryForList(
            "SELECT id, source_asset_version_id, chapter_id, start_codepoint, end_codepoint, text,"
                + " text_sha256, word_count, sentence_count, generation_method, generator_version,"
                + " created_at FROM analytics.excerpt WHERE id=?",
            excerptId);
    return rows.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(rows.getFirst());
  }
}
