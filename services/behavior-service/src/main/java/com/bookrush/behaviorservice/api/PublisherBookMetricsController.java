package com.bookrush.behaviorservice.api;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Only the scoped publisher-service identity may read this server-side projection. */
@RestController
@RequestMapping("/api/internal/v1/publisher/metrics/books")
public class PublisherBookMetricsController {
  private final JdbcTemplate jdbc;
  public PublisherBookMetricsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @GetMapping("/{bookId}")
  public Object metrics(@PathVariable UUID bookId, @RequestParam(defaultValue="30") int days) {
    if (days < 1 || days > 365) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid days");
    return jdbc.queryForList("""
        SELECT day, impressions, opens, reads, likes, shares, comments FROM behavior.daily_book
        WHERE book_id=? AND day >= current_date - (? - 1) AND day <= current_date
        ORDER BY day DESC
        """, bookId, days);
  }
}
