package com.bookrush.catalog.canonical;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/internal/v1/catalog")
@Tag(name = "Catálogo interno", description = "Boundary idempotente usado pelo ingestion-service para projetar obras, edições, autores e identificadores.")
@SecurityRequirement(name = "catalogService")
public class CanonicalCommandController {
  private final CanonicalCommandService service;
  public CanonicalCommandController(CanonicalCommandService service) { this.service = service; }

  @PostMapping("/commands")
  @Operation(summary = "Aplicar comando canônico", description = "Cria ou atualiza uma obra a partir de identificador externo. operationKey e requestHash tornam o retry idempotente; fenceToken rejeita workers obsoletos.")
  public ResponseEntity<Map<String, Object>> apply(@Valid @RequestBody CanonicalCommand command) {
    return ResponseEntity.ok(service.apply(command));
  }

  @ExceptionHandler(CanonicalCommandService.CanonicalConflict.class)
  ResponseEntity<Map<String, String>> conflict(CanonicalCommandService.CanonicalConflict e) {
    return ResponseEntity.status(409).body(Map.of("code", "CANONICAL_CONFLICT", "message", e.getMessage()));
  }
}
