package com.bookrush.behaviorservice.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Internal aggregate contract for publisher self-service reporting. */
@RestController
@RequestMapping("/api/internal/v1/publisher/metrics")
public class PublisherBehaviorMetricsController {
  private static final int MIN_READERS = 5;
  private static final int GROWTH_WINDOW_DAYS = 30;
  private final JdbcTemplate jdbc;

  public PublisherBehaviorMetricsController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/books/{bookId}/summary")
  public Map<String, Object> bookMetrics(@PathVariable UUID bookId,
      @RequestParam(defaultValue = "90") int days) {
    if (days < 1 || days > 366) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "days must be between 1 and 366");
    }
    var source = jdbc.queryForList("""
        SELECT m.day, m.impressions, m.opens, m.reads,
            count(DISTINCT u.identity_subject) FILTER (WHERE u.reads > 0) AS unique_readers
        FROM behavior.daily_book m
        LEFT JOIN behavior.daily_user_book u ON u.day = m.day AND u.book_id = m.book_id
        WHERE m.book_id = ? AND m.day >= current_date - (? - 1)
        GROUP BY m.day, m.impressions, m.opens, m.reads
        ORDER BY m.day
        """, bookId, days);
    List<Map<String, Object>> items = new ArrayList<>(source.size());
    for (var row : source) {
      long readers = ((Number) row.get("unique_readers")).longValue();
      var item = new HashMap<String, Object>();
      item.put("day", row.get("day"));
      item.put("suppressed", readers < MIN_READERS);
      if (readers >= MIN_READERS) {
        item.put("uniqueReaders", readers);
        item.put("impressions", row.get("impressions"));
        item.put("opens", row.get("opens"));
        item.put("reads", row.get("reads"));
      }
      items.add(item);
    }

    var periods = jdbc.queryForList("""
        WITH bounds AS (
          SELECT current_date - (? - 1) AS current_start,
                 current_date - (? * 2 - 1) AS previous_start,
                 current_date - (? - 1) AS previous_end
        ), totals AS (
          SELECT coalesce(sum(m.impressions) FILTER (WHERE m.day >= b.current_start), 0) AS current_impressions,
              coalesce(sum(m.opens) FILTER (WHERE m.day >= b.current_start), 0) AS current_opens,
              coalesce(sum(m.reads) FILTER (WHERE m.day >= b.current_start), 0) AS current_reads,
              coalesce(sum(m.impressions) FILTER (WHERE m.day >= b.previous_start AND m.day < b.previous_end), 0) AS previous_impressions,
              coalesce(sum(m.opens) FILTER (WHERE m.day >= b.previous_start AND m.day < b.previous_end), 0) AS previous_opens,
              coalesce(sum(m.reads) FILTER (WHERE m.day >= b.previous_start AND m.day < b.previous_end), 0) AS previous_reads
          FROM bounds b LEFT JOIN behavior.daily_book m ON m.book_id = ?
              AND m.day >= b.previous_start AND m.day <= current_date
        ), audience AS (
          SELECT count(DISTINCT u.identity_subject) FILTER (WHERE u.day >= b.current_start AND u.reads > 0) AS current_readers,
              count(DISTINCT u.identity_subject) FILTER (WHERE u.day >= b.previous_start AND u.day < b.previous_end AND u.reads > 0) AS previous_readers
          FROM bounds b LEFT JOIN behavior.daily_user_book u ON u.book_id = ?
              AND u.day >= b.previous_start AND u.day <= current_date
        )
        SELECT * FROM totals CROSS JOIN audience
        """, GROWTH_WINDOW_DAYS, GROWTH_WINDOW_DAYS, GROWTH_WINDOW_DAYS, bookId, bookId);
    var growth = new HashMap<String, Object>();
    if (!periods.isEmpty()) {
      var row = periods.getFirst();
      long currentReaders = ((Number) row.get("current_readers")).longValue();
      long previousReaders = ((Number) row.get("previous_readers")).longValue();
      boolean eligible = currentReaders >= MIN_READERS && previousReaders >= MIN_READERS;
      growth.put("windowDays", GROWTH_WINDOW_DAYS);
      growth.put("suppressed", !eligible);
      if (eligible) {
        growth.put("currentReaders", currentReaders);
        growth.put("previousReaders", previousReaders);
        for (String metric : List.of("impressions", "opens", "reads")) {
          long current = ((Number) row.get("current_" + metric)).longValue();
          long previous = ((Number) row.get("previous_" + metric)).longValue();
          growth.put("current_" + metric, current);
          growth.put("previous_" + metric, previous);
          growth.put(metric + "_change_percent", previous == 0 ? null
              : Math.round((current - previous) * 10000.0 / previous) / 100.0);
        }
      }
    }
    return Map.of("bookId", bookId, "days", days, "minReaders", MIN_READERS,
        "items", items, "growth", growth);
  }
}
