package com.bookrush.behaviorservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.behaviorservice.api.RecommendationSignalsController;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class UserBookSignalPostgresIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    DataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA IF EXISTS behavior CASCADE");
    jdbc.execute("CREATE SCHEMA behavior");
    jdbc.execute("""
        CREATE TABLE behavior.events (
          id UUID PRIMARY KEY, event_key VARCHAR(200) NOT NULL UNIQUE,
          event_type VARCHAR(80) NOT NULL, identity_issuer TEXT,
          identity_subject TEXT, book_id UUID, payload JSONB NOT NULL DEFAULT '{}'::jsonb,
          occurred_at TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now())
        """);
    jdbc.execute("CREATE TABLE behavior.daily_book (day DATE NOT NULL, book_id UUID NOT NULL, impressions BIGINT NOT NULL DEFAULT 0, opens BIGINT NOT NULL DEFAULT 0, likes BIGINT NOT NULL DEFAULT 0, reads BIGINT NOT NULL DEFAULT 0, shares BIGINT NOT NULL DEFAULT 0, comments BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(day, book_id))");
    jdbc.execute("CREATE TABLE behavior.outcome_attribution (id UUID PRIMARY KEY, subject_key TEXT NOT NULL, book_id UUID NOT NULL, outcome_type VARCHAR(40) NOT NULL, source_event_id UUID NOT NULL REFERENCES behavior.events(id) ON DELETE RESTRICT, occurred_at TIMESTAMPTZ NOT NULL, UNIQUE(source_event_id, outcome_type))");
    jdbc.execute("CREATE TABLE behavior.rejects (id UUID PRIMARY KEY, event_key TEXT, reason TEXT NOT NULL, payload JSONB NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT now())");
    jdbc.execute("CREATE TABLE behavior.outbox (id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, event_type TEXT NOT NULL, payload JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ)");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__user_book_serving_signals.sql"))
        .execute(dataSource);
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__recommendation_outcome_correlation.sql"))
        .execute(dataSource);
  }

  @Test
  void rebuildIsIdempotentAndKeepsLatestReversibleState() {
    UUID bookA = UUID.randomUUID();
    UUID bookB = UUID.randomUUID();
    UUID recommendationItem = UUID.randomUUID();
    UUID impression = UUID.randomUUID();
    Instant base = Instant.parse("2026-10-20T10:00:00Z");
    insert("user-a", bookA, "BOOK_LIKE", "{}", base.plusSeconds(1));
    insert("user-a", bookA, "BOOK_UNLIKE", "{}", base.plusSeconds(2));
    insert("user-a", bookA, "BOOK_SAVE", "{}", base.plusSeconds(3));
    insert("user-a", bookA, "BOOK_UNSAVE", "{}", base.plusSeconds(4));
    insert("user-a", bookA, "EXCERPT_IMPRESSION", viewableCorrelationPayload(recommendationItem, impression), base.plusSeconds(5));
    insert("user-a", bookA, "BOOK_OPEN", correlationPayload(recommendationItem, impression), base.plusSeconds(6));
    insert("user-a", bookA, "READ_PROGRESS", "{\"percent\":42.5}", base.plusSeconds(7));
    insert("user-a", bookA, "READ_SESSION", "{\"activeSeconds\":120}", base.plusSeconds(8));
    insert("user-a", bookA, "BOOK_COMPLETE", "{}", base.plusSeconds(9));
    insert("user-b", bookB, "BOOK_LIKE", "{}", base.plusSeconds(10));
    insert("user-b", bookB, "BOOK_OPEN", "{}", base.plusSeconds(11));

    BehaviorMaintenance maintenance = new BehaviorMaintenance(jdbc, true, 30);
    maintenance.rebuildNow();
    Map<String, Object> first = signal("user-a", bookA);
    maintenance.rebuildNow();
    Map<String, Object> second = signal("user-a", bookA);
    var response = new RecommendationSignalsController(jdbc).signals(
        new RecommendationSignalsController.SignalsRequest("user-a", List.of(bookA), 10, 0));

    assertEquals(first, second);
    assertEquals(200, response.getStatusCode().value());
    var body = (RecommendationSignalsController.SignalsResponse) response.getBody();
    assertEquals(1, body.items().size());
    assertEquals(bookA, body.items().get(0).bookId());
    assertEquals(1, body.total());
    assertEquals(false, first.get("liked"));
    assertEquals(false, first.get("saved"));
    assertEquals(1L, first.get("impression_count"));
    assertEquals(1L, first.get("viewable_impression_count"));
    assertEquals(1L, first.get("open_count"));
    assertEquals(1L, first.get("progress_event_count"));
    assertEquals(1L, first.get("completion_count"));
    assertEquals(120L, first.get("active_seconds"));
    assertEquals("42.50", first.get("max_progress").toString());
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM behavior.user_book_signal", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM behavior.outcome_attribution WHERE subject_key = 'user-a' AND recommendation_item_id = ? AND impression_id = ?", Integer.class, recommendationItem, impression));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM behavior.outcome_attribution WHERE subject_key = 'user-a' AND outcome_type = 'BOOK_OPEN' AND recommendation_item_id = ?", Integer.class, recommendationItem));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM behavior.outcome_attribution WHERE subject_key = 'user-b' AND outcome_type = 'BOOK_OPEN' AND recommendation_item_id IS NULL AND impression_id IS NULL", Integer.class));

    insert("user-b", bookB, "BOOK_LIKE", correlationPayload(recommendationItem, impression), base.plusSeconds(12));
    maintenance.rebuildNow();
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM behavior.outcome_attribution WHERE subject_key = 'user-b' AND recommendation_item_id = ? AND impression_id = ?", Integer.class, recommendationItem, impression));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM behavior.outcome_attribution WHERE subject_key = 'user-a' AND recommendation_item_id = ? AND impression_id = ?", Integer.class, recommendationItem, impression));
  }

  private String correlationPayload(UUID itemId, UUID impressionId) {
    return "{\"recommendationItemId\":\"" + itemId + "\",\"impressionId\":\"" + impressionId + "\"}";
  }

  private String viewableCorrelationPayload(UUID itemId, UUID impressionId) {
    return "{\"recommendationItemId\":\"" + itemId + "\",\"impressionId\":\"" + impressionId + "\",\"viewable\":true}";
  }

  private void insert(String subject, UUID bookId, String type, String payload, Instant occurredAt) {
    jdbc.update("INSERT INTO behavior.events(id, event_key, event_type, identity_subject, book_id, payload, occurred_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
        UUID.randomUUID(), UUID.randomUUID().toString(), type, subject, bookId, payload, Timestamp.from(occurredAt));
  }

  private Map<String, Object> signal(String subject, UUID bookId) {
    return jdbc.queryForMap("SELECT impression_count, viewable_impression_count, open_count, progress_event_count, completion_count, active_seconds, max_progress, liked, saved FROM behavior.user_book_signal WHERE identity_subject = ? AND book_id = ?", subject, bookId);
  }
}
