package com.bookrush.catalog;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Status", description = "Verificação pública da disponibilidade do catálogo e da conexão com PostgreSQL.")
class StatusController {
  private final JdbcTemplate jdbc;

  StatusController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping({"", "/"})
  @Operation(summary = "Informações básicas da API", description = "Retorna o nome do serviço e o caminho do healthcheck detalhado.")
  ResponseEntity<Map<String, String>> apiInfo() {
    return ResponseEntity.ok(Map.of(
        "service", "catalog-service",
        "status", "ok",
        "health", "/api/status"));
  }

  @GetMapping("/status")
  @Operation(summary = "Verificar catálogo e banco", description = "Executa uma consulta simples no PostgreSQL. Retorna 503 quando o banco não está disponível.")
  ResponseEntity<Map<String, String>> status() {
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(Map.of("service", "catalog-service", "status", "ok", "database", "up"));
    } catch (DataAccessException exception) {
      return ResponseEntity.status(503).body(Map.of("service", "catalog-service", "status", "error", "database", "down"));
    }
  }
}
