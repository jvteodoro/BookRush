package com.bookrush.ingestion.jobs;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/v1/ingestion")
@Tag(name = "Ingestão administrativa", description = "Executa e acompanha importações controladas. Requer JWT do Keycloak com role OPERATOR, REVIEWER ou CLEANUP conforme a operação.")
@SecurityRequirement(name = "keycloak")
public class IngestionAdminController {
  private final IngestionJobService jobs;
  public IngestionAdminController(IngestionJobService jobs) { this.jobs = jobs; }
  @PostMapping("/run")
  @Operation(summary = "Iniciar uma execução de ingestão", description = "Submete uma execução assíncrona. Use Idempotency-Key estável para retries; a resposta 202 contém o jobId e a URL de acompanhamento.", responses = {
      @ApiResponse(responseCode = "202", description = "Execução aceita"),
      @ApiResponse(responseCode = "200", description = "Requisição repetida; resultado idempotente devolvido"),
      @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
      @ApiResponse(responseCode = "409", description = "Conflito de idempotência ou parâmetros inválidos")})
  public ResponseEntity<Map<String, Object>> run(@Valid @RequestBody IngestionRunRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestHeader(value = "X-Principal", required = false) String principal) {
    var result = jobs.submit(request, idempotencyKey, principal);
    return ResponseEntity.status(result.replay() ? 200 : 202).location(URI.create(String.valueOf(result.body().get("location")))).body(result.body());
  }
  @GetMapping("/jobs/{jobId}") @Operation(summary = "Consultar execução", description = "Retorna o estado, contadores, erros e timestamps de um job.") public Map<String, Object> job(@PathVariable UUID jobId) { return jobs.getJob(jobId); }
  @GetMapping("/jobs/{jobId}/items") @Operation(summary = "Listar itens de uma execução", description = "Retorna os itens descobertos e seus estados, com paginação.") public Map<String, Object> items(@PathVariable UUID jobId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) { return Map.of("jobId", jobId, "page", page, "size", size, "items", jobs.getItems(jobId, page, size)); }
  @PostMapping("/jobs/{jobId}/cancel") @Operation(summary = "Cancelar execução", description = "Solicita cancelamento cooperativo de um job em execução.") public Map<String, Object> cancel(@PathVariable UUID jobId, @RequestHeader(value = "X-Principal", required = false) String principal) { return jobs.cancel(jobId, principal); }
  @PostMapping("/jobs/{jobId}/resume") @Operation(summary = "Retomar execução", description = "Retoma itens elegíveis de um job interrompido, preservando tentativas e fencing.") public Map<String, Object> resume(@PathVariable UUID jobId, @RequestHeader(value = "X-Principal", required = false) String principal) { return jobs.resume(jobId, principal); }
  @GetMapping("/state") @Operation(summary = "Consultar estado mais recente", description = "Retorna o último job conhecido ou estado IDLE quando não há execução.") public Map<String, Object> state() { return jobs.latestJob(); }
  @PostMapping("/cancel") @Operation(summary = "Cancelar o job mais recente", description = "Atalho operacional para cancelar a execução mais recente.") public Map<String, Object> cancelLatest(@RequestHeader(value = "X-Principal", required = false) String principal) {
    var state = jobs.latestJob();
    return state.get("id") == null ? state : jobs.cancel(UUID.fromString(String.valueOf(state.get("id"))), principal);
  }
  @ExceptionHandler(IngestionJobService.IdempotencyConflictException.class) ResponseEntity<Map<String,String>> idempotencyConflict(RuntimeException e) { return ResponseEntity.status(409).body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", e.getMessage())); }
  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class}) ResponseEntity<Map<String,String>> conflict(RuntimeException e) { return ResponseEntity.status(409).body(Map.of("code", "INVALID_REQUEST", "message", e.getMessage())); }
  @ExceptionHandler(IngestionJobService.JobNotFoundException.class) ResponseEntity<Map<String,String>> notFound(RuntimeException e) { return ResponseEntity.status(404).body(Map.of("code", "JOB_NOT_FOUND", "message", e.getMessage())); }
}
