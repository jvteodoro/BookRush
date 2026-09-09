package com.bookrush.ingestion.jobs;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/operations")
public class OperationsController {
  private final ReconciliationJob reconciliation;
  public OperationsController(ReconciliationJob reconciliation) { this.reconciliation = reconciliation; }
  @GetMapping("/stale-tasks") public Map<String,Object> stale() { return Map.of("tasks", reconciliation.staleTasks()); }
  @PostMapping("/requeue-expired") public Map<String,Object> requeue() { return Map.of("requeued", reconciliation.requeueExpired()); }
}
