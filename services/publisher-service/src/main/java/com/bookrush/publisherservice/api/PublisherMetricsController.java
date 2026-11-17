package com.bookrush.publisherservice.api;

import java.security.Principal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.bookrush.publisherservice.PublisherBehaviorMetricsClient;

@RestController
@RequestMapping("/api/v1/publisher")
public class PublisherMetricsController {
  private final JdbcTemplate jdbc;
  private final PublisherBehaviorMetricsClient behavior;

  public PublisherMetricsController(JdbcTemplate jdbc, PublisherBehaviorMetricsClient behavior) {
    this.jdbc = jdbc;
    this.behavior = behavior;
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    return principal.getName();
  }

  @GetMapping("/submissions/{id}/metrics")
  public Object metrics(@PathVariable UUID id, Principal principal) {
    var rows = jdbc.queryForList(
        "SELECT catalog_book_id FROM publisher.submission WHERE id=? AND subject_key=?",
        id, subject(principal));
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
    Object book = rows.getFirst().get("catalog_book_id");
    if (book == null)
      return Map.of("submissionId", id, "status", "NOT_LINKED", "items", List.of(),
          "growth", Map.of());
    Object report = behavior.bookMetrics(book);
    var response = new HashMap<String, Object>();
    response.put("submissionId", id);
    response.put("catalogBookId", book);
    response.put("status", "READY");
    if (report instanceof Map<?, ?> values)
      values.forEach((key, value) -> response.put(String.valueOf(key), value));
    else {
      response.put("items", List.of());
      response.put("growth", Map.of());
    }
    return response;
  }
}
