package com.bookrush.behaviorservice;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.behaviorservice.api.PublisherBookMetricsController;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit integration test: TEST_DATABASE_URL must name a disposable PostgreSQL database. */
class BehaviorMaintenancePostgresIT {
  @Test
  void scheduledTransactionCommitsPublisherTotalsAndRetentionWithoutCountingUnlikes() {
    var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
        "bookrush_test", "test-only-not-for-production");
    Flyway.configure().dataSource(source).schemas("behavior").load().migrate();
    var jdbc = new JdbcTemplate(source);
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    var maintenance = new BehaviorMaintenance(jdbc, true, 30);
    UUID book = UUID.randomUUID(), oldReject = UUID.randomUUID(), recentReject = UUID.randomUUID();
    UUID oldPublished = UUID.randomUUID(), recentPublished = UUID.randomUUID(), pending = UUID.randomUUID();
    try {
      for (String type : List.of("EXCERPT_IMPRESSION", "EXCERPT_IMPRESSION", "BOOK_OPEN",
          "READ_PROGRESS", "READ_PROGRESS", "BOOK_COMPLETE", "BOOK_LIKE", "BOOK_UNLIKE",
          "BOOK_SHARE", "BOOK_COMMENT")) {
        UUID event = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO behavior.events(id,event_key,event_type,identity_subject,book_id,occurred_at)
            VALUES (?,?,?,?,?,now())
            """, event, event.toString(), type, "fixture-" + book, book);
      }
      jdbc.update("""
          INSERT INTO behavior.rejects(id,reason,payload,received_at)
          VALUES (?,'fixture','{}',now()-interval '60 days'), (?,'fixture','{}',now())
          """, oldReject, recentReject);
      jdbc.update("""
          INSERT INTO behavior.outbox(id,event_id,event_type,payload,occurred_at,created_at,published_at)
          VALUES (?,?,'fixture','{}',now(),now()-interval '60 days',now()),
                 (?,?,'fixture','{}',now(),now(),now()),
                 (?,?,'fixture','{}',now(),now()-interval '60 days',NULL)
          """, oldPublished, UUID.randomUUID(), recentPublished, UUID.randomUUID(), pending, UUID.randomUUID());

      // Match the scheduled @Transactional entrypoint, including cleanup after rebuilding.
      // Calling rebuildNow() alone would miss a cleanup error that rolls back all totals.
      for (int run = 0; run < 2; run++) {
        transaction.executeWithoutResult(status -> maintenance.run());
        var rows = (List<Map<String, Object>>) new PublisherBookMetricsController(jdbc).metrics(book, 7);
        assertEquals(1, rows.size());
        var totals = rows.getFirst();
        for (var expected : Map.of("impressions", 2L, "opens", 1L, "reads", 3L,
            "likes", 1L, "shares", 1L, "comments", 1L).entrySet()) {
          assertEquals(expected.getValue(), ((Number) totals.get(expected.getKey())).longValue(),
              expected.getKey() + " after run " + run);
        }
      }
      assertEquals(0, count(jdbc, "rejects", oldReject));
      assertEquals(1, count(jdbc, "rejects", recentReject));
      assertEquals(0, count(jdbc, "outbox", oldPublished));
      assertEquals(1, count(jdbc, "outbox", recentPublished));
      assertEquals(1, count(jdbc, "outbox", pending));
      assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM behavior.events WHERE book_id=?",
          Integer.class, book));
    } finally {
      jdbc.update("DELETE FROM behavior.outcome_attribution WHERE book_id=?", book);
      jdbc.update("DELETE FROM behavior.user_book_signal WHERE book_id=?", book);
      jdbc.update("DELETE FROM behavior.daily_book WHERE book_id=?", book);
      jdbc.update("DELETE FROM behavior.events WHERE book_id=?", book);
      jdbc.update("DELETE FROM behavior.rejects WHERE id IN (?,?)", oldReject, recentReject);
      jdbc.update("DELETE FROM behavior.outbox WHERE id IN (?,?,?)", oldPublished, recentPublished, pending);
    }
  }

  private int count(JdbcTemplate jdbc, String table, UUID id) {
    return jdbc.queryForObject("SELECT count(*) FROM behavior." + table + " WHERE id=?", Integer.class, id);
  }
}
