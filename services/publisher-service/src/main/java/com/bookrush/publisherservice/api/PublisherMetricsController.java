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
  private final PublisherIdentity identities;
  static final String QUERY = """
      SELECT w.book_id AS catalog_book_id FROM publisher.submission s
      LEFT JOIN publisher.published_work w ON w.submission_id=s.id
        AND w.identity_issuer=s.identity_issuer AND w.identity_subject=s.identity_subject
      WHERE s.id=? AND s.identity_issuer=? AND s.identity_subject=?
      """;

  PublisherMetricsController(JdbcTemplate jdbc, PublisherMetricsClient behavior, PublisherIdentity identities) {
    this.jdbc = jdbc;
    this.behavior = behavior;
    this.identities = identities;
  }
  @GetMapping("/submissions/{id}/metrics")
  public Object metrics(@PathVariable UUID id, Principal principal,
      @RequestParam(defaultValue="30") int days) {
    var actor = identities.author(principal);
    if (days < 1 || days > 365)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 365");
    var rows = jdbc.queryForList(QUERY, id, actor.issuer(), actor.subject());
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
    Object book = rows.getFirst().get("catalog_book_id");
    if (book == null) return Map.of("submissionId", id, "status", "NOT_LINKED", "days", days, "items", List.of());
    var data = behavior.metrics(UUID.fromString(book.toString()), days);
    if (data == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metrics unavailable");
    return Map.of("submissionId", id, "catalogBookId", book, "status", "AVAILABLE", "days", days, "items", data);
  }
}
