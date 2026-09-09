package com.bookrush.catalog.canonical;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/v1/catalog")
public class CanonicalCommandController {
  private final CanonicalCommandService service;
  public CanonicalCommandController(CanonicalCommandService service) { this.service = service; }

  @PostMapping("/commands")
  public ResponseEntity<Map<String, Object>> apply(@Valid @RequestBody CanonicalCommand command) {
    return ResponseEntity.ok(service.apply(command));
  }

  @ExceptionHandler(CanonicalCommandService.CanonicalConflict.class)
  ResponseEntity<Map<String, String>> conflict(CanonicalCommandService.CanonicalConflict e) {
    return ResponseEntity.status(409).body(Map.of("code", "CANONICAL_CONFLICT", "message", e.getMessage()));
  }
}
