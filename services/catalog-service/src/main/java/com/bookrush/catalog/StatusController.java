package com.bookrush.catalog;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class StatusController {
  private final JdbcTemplate jdbc;

  StatusController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping({"", "/"})
  ResponseEntity<Map<String, String>> apiInfo() {
    return ResponseEntity.ok(Map.of(
        "service", "catalog-service",
        "status", "ok",
        "health", "/api/status"));
  }

  @GetMapping("/status")
  ResponseEntity<Map<String, String>> status() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(Map.of("service", "catalog-service", "status", "ok", "database", "up"));
    } catch (DataAccessException exception) {
      return ResponseEntity.status(503).body(Map.of("service", "catalog-service", "status", "error", "database", "down"));
    }
  }
}
