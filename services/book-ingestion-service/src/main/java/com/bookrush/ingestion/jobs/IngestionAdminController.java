package com.bookrush.ingestion.jobs;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/ingestion")
public class IngestionAdminController {
  private final BootstrapPipeline pipeline;
  public IngestionAdminController(BootstrapPipeline pipeline) { this.pipeline = pipeline; }
  @PostMapping("/run") public Map<String, Object> run(@RequestParam(defaultValue = "false") boolean dryRun) { return Map.of("dryRun", dryRun, "state", dryRun ? pipeline.state() : pipeline.start()); }
  @PostMapping("/cancel") public Map<String, Object> cancel() { return Map.of("state", pipeline.cancel()); }
  @GetMapping("/state") public Map<String, Object> state() { return Map.of("state", pipeline.state()); }
  @ExceptionHandler(IllegalStateException.class) ResponseEntity<Map<String,String>> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(Map.of("code", "INVALID_STATE", "message", e.getMessage())); }
}
