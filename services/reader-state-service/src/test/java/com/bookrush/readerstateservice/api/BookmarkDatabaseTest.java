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
    Flyway.configure().dataSource(ds).schemas("reader_state").target("10").load().migrate();
    UUID upgradeBook = UUID.randomUUID();
    jdbc.update("INSERT INTO reader_state.bookmarks(id, subject_key, book_id, position_codepoint, locator) VALUES (?,?,?,?,?::jsonb)",
        UUID.randomUUID(), reader, upgradeBook, 0L, "{\"href\":\"chapter\",\"type\":\"text/html\",\"locations\":{\"progression\":0.25}}");
    Flyway.configure().dataSource(ds).schemas("reader_state").target("11").load().migrate();
    var reopened = UUID.randomUUID();
    var ambiguous = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO reader_state.seen_book(subject_key,book_id,first_seen_at,last_seen_at)
        SELECT ?,?, clock_timestamp()-interval '1 hour', clock_timestamp()
        """, reader, reopened);
    jdbc.update("""
        INSERT INTO reader_state.seen_book(subject_key,book_id,first_seen_at,last_seen_at)
        SELECT ?,?, t,t FROM (SELECT clock_timestamp() t) evidence
        """, reader, ambiguous);
    var latest = Flyway.configure().dataSource(ds).schemas("reader_state").load();
    latest.migrate();
    latest.validate();
    latest.migrate();
    assertEquals(List.of(reopened), jdbc.queryForList(
        "SELECT book_id FROM reader_state.book_open WHERE subject_key=?", UUID.class, reader));
    var controller = new ReaderStateController(jdbc, null, null);
    var upgraded = (List<?>) controller.bookmarks(upgradeBook, () -> reader);
    assertEquals("", ((Map<?, ?>) upgraded.get(0)).get("name"));
    assertTrue(((Map<?, ?>) upgraded.get(0)).get("locator").toString().contains("0.25"));
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
    UUID firstId = (UUID) first.get("id");
    var renamed = controller.renameBookmark(book, firstId, Map.of("name", "  Meu trecho  "), () -> reader);
    assertEquals("Meu trecho", renamed.get("name"));
    assertEquals(first.get("locator"), renamed.get("locator"));
    assertEquals(first.get("created_at"), renamed.get("created_at"));
    assertEquals("Meu trecho", controller.bookmark(book, Map.of("locator", locator), () -> reader).get("name"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> controller.renameBookmark(book, firstId, Map.of("name", "Outro"), () -> "another-reader"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> controller.deleteBookmark(UUID.randomUUID(), firstId, () -> reader));
    controller.deleteBookmark(book, firstId, () -> reader);
    assertEquals(2, ((List<?>) controller.bookmarks(book, () -> reader)).size());
    assertEquals(1, ((List<?>) controller.bookmarks(book, () -> "another-reader")).size());

    var progressController = new ReaderStateController(jdbc,
        org.mockito.Mockito.mock(com.bookrush.readerstateservice.BehaviorEventPublisher.class), null);
    Map<String, Object> savedPosition = Map.of("href", "/versions/v1/chapters/c1/content", "type", "text/html",
        "locations", Map.of("progression", 0.432123));
    progressController.progress(book, new ReaderStateController.Progress(0L, 43.2123, savedPosition), () -> reader, null);
    var rows = (List<?>) progressController.getProgress(book, () -> reader);
    assertEquals(1, rows.size());
    assertTrue(((Map<?, ?>) rows.get(0)).get("locator").toString().contains("0.432123"));
    assertEquals(0, ((List<?>) progressController.getProgress(book, () -> "another-reader")).size());
    progressController.progress(book, new ReaderStateController.Progress(0L, 0d), () -> reader, null);
    rows = (List<?>) progressController.getProgress(book, () -> reader);
    assertNull(((Map<?, ?>) rows.get(0)).get("locator"));
  }
}
