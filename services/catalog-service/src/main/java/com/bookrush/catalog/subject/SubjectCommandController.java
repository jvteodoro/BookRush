package com.bookrush.catalog.subject;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/internal/v1/catalog/subjects")
@Tag(name="Subjects internos", description="Atribuições bibliográficas idempotentes com scheme e proveniência de fonte.")
public class SubjectCommandController {
  private final SubjectCommandService service;
  public SubjectCommandController(SubjectCommandService service) { this.service = service; }
  @PostMapping
  @Operation(summary="Atribuir subject a uma obra", description="Normaliza somente para lookup e preserva o termo exibido; repetição da mesma atribuição retorna NOOP.")
  public ResponseEntity<Map<String,Object>> assign(@Valid @RequestBody SubjectCommandService.SubjectCommand command) { return ResponseEntity.ok(service.assign(command)); }
  @ExceptionHandler(SubjectCommandService.SubjectConflict.class)
  ResponseEntity<Map<String,String>> conflict(SubjectCommandService.SubjectConflict e) { return ResponseEntity.status(409).body(Map.of("code","SUBJECT_CONFLICT","message",e.getMessage())); }
}
