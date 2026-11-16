package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The runner must supply an empty, disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "BOOKMARK_TEST_JDBC_URL", matches = ".+")
class BookmarkDatabaseTest {
  @Test
  void upgradesLegacyMarksAndPreservesPreciseLocatorsAcrossReaders() {
    var ds = new DriverManagerDataSource(System.getenv("BOOKMARK_TEST_JDBC_URL"), "postgres", "bookrush-test");
    var before = Flyway.configure().dataSource(ds).schemas("reader_state").target("9").load();
    before.migrate();
    var jdbc = new JdbcTemplate(ds);
    var book = UUID.randomUUID();
    var reader = "bookmark-test-" + UUID.randomUUID();
    var legacyId = UUID.randomUUID();
    jdbc.update("INSERT INTO reader_state.bookmarks(id, subject_key, book_id, position_codepoint) VALUES (?,?,?,?)",
        legacyId, reader, book, 43L);
    var latest = Flyway.configure().dataSource(ds).schemas("reader_state").load();
    latest.migrate();
    latest.validate();
    latest.migrate();
    var controller = new ReaderStateController(jdbc, null, null);
    var locator = Map.of("href", "/versions/v1/chapters/c1/content", "type", "text/html",
        "locations", Map.of("progression", 0.4321));
    var first = controller.bookmark(book, Map.of("locator", locator), () -> reader);
    var same = controller.bookmark(book, Map.of("locator", locator), () -> reader);
    assertEquals(first.get("id"), same.get("id"));
    var nearby = Map.of("href", locator.get("href"), "type", "text/html",
        "locations", Map.of("progression", 0.4322));
    var second = controller.bookmark(book, Map.of("locator", nearby), () -> reader);
    assertNotEquals(first.get("id"), second.get("id"));
    assertTrue(first.get("locator").toString().contains("0.4321"));
    assertNotNull(first.get("created_at"));
    assertNotNull(jdbc.queryForObject("SELECT created_at FROM reader_state.bookmarks WHERE id=?",
        (rs, row) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant(), first.get("id")));
    assertTrue(first.get("id") instanceof UUID);
    assertEquals(3, ((List<?>) controller.bookmarks(book, () -> reader)).size());
    assertEquals(0, ((List<?>) controller.bookmarks(book, () -> "another-reader")).size());
    var other = controller.bookmark(book, Map.of("locator", locator), () -> "another-reader");
    assertNotEquals(first.get("id"), other.get("id"));
    assertEquals(legacyId, controller.bookmark(book, Map.of("positionCodepoint", 43L), () -> reader).get("id"));
  }
}
