package com.bookrush.publisherservice.api;

import com.bookrush.publisherservice.PublisherMetricsClient;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/publisher")
public class PublisherMetricsController {
  private final JdbcTemplate jdbc;
  private final PublisherMetricsClient behavior;
  public PublisherMetricsController(JdbcTemplate jdbc, PublisherMetricsClient behavior) {
    this.jdbc = jdbc; this.behavior = behavior;
  }
  @GetMapping("/submissions/{id}/metrics")
  public Object metrics(@PathVariable UUID id, Principal principal,
      @RequestParam(defaultValue="30") int days) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    if (days < 1 || days > 365)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 365");
    var rows = jdbc.queryForList("select w.book_id as catalog_book_id from publisher.submission s left join publisher.published_work w on w.submission_id=s.id and w.publisher_subject_key=s.subject_key where s.id=? and s.subject_key=?", id, principal.getName());
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
    Object book = rows.getFirst().get("catalog_book_id");
    if (book == null) return Map.of("submissionId", id, "status", "NOT_LINKED", "days", days, "items", List.of());
    var data = behavior.metrics(UUID.fromString(book.toString()), days);
    if (data == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metrics unavailable");
    return Map.of("submissionId", id, "catalogBookId", book, "status", "AVAILABLE", "days", days, "items", data);
  }
}
