package com.bookrush.ingestion.jobs;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/v1/operations")
@Tag(name = "Operações", description = "Rotinas de recuperação para tarefas expiradas e falhas operacionais.")
@SecurityRequirement(name = "keycloak")
public class OperationsController {
  private final ReconciliationJob reconciliation;
  public OperationsController(ReconciliationJob reconciliation) { this.reconciliation = reconciliation; }
  @GetMapping("/stale-tasks") @Operation(summary = "Listar tarefas obsoletas", description = "Identifica leases expirados que podem exigir reconciliação.") public Map<String,Object> stale() { return Map.of("tasks", reconciliation.staleTasks()); }
  @PostMapping("/requeue-expired") @Operation(summary = "Reenfileirar tarefas expiradas", description = "Recoloca tarefas expiradas em estado elegível para nova tentativa.") public Map<String,Object> requeue() { return Map.of("requeued", reconciliation.requeueExpired()); }
}
