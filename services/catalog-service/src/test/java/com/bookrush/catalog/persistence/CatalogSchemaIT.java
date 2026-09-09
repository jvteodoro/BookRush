package com.bookrush.catalog.persistence;

import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Database invariants are tested on PostgreSQL, including deferrable constraints. */
@SpringBootTest
class CatalogSchemaIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  private UUID book(String title) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book(id,canonical_title) VALUES (?,?)", id, title);
    return id;
  }

  private UUID author(String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.author(id,name) VALUES (?,?)", id, name);
    return id;
  }

  @Test
  void acceptsIncompleteMetadataAndDuplicateNamesAndTitles() {
    UUID first = book("Pride and Prejudice");
    UUID second = book("Pride and Prejudice");
    assertNotEquals(first, second);
    assertNotEquals(author("Jane Austen"), author("Jane Austen"));
    jdbc.update("INSERT INTO catalog.edition(id,book_id) VALUES (?,?)", UUID.randomUUID(), first);
    assertNull(jdbc.queryForObject("SELECT original_language FROM catalog.book WHERE id=?", String.class, first));
    assertEquals("REVIEW_REQUIRED", jdbc.queryForObject("SELECT status FROM catalog.book WHERE id=?", String.class, first));
  }

  @Test
  void translationCreditBelongsToEditionWithoutChangingWorkCredits() {
    UUID work = book("Dom Casmurro");
    UUID writer = author("Machado de Assis");
    UUID translator = author("Translator fixture");
    UUID edition = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.edition(id,book_id,language) VALUES (?,?,'en')", edition, work);
    jdbc.update("INSERT INTO catalog.book_author VALUES (?,?,'AUTHOR',1)", work, writer);
    jdbc.update("INSERT INTO catalog.edition_author VALUES (?,?,'TRANSLATOR',1)", edition, translator);
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM catalog.book_author WHERE author_id=?", Integer.class, translator));
    assertEquals(work, jdbc.queryForObject("SELECT e.book_id FROM catalog.edition_author c JOIN catalog.edition e ON e.id=c.edition_id WHERE c.author_id=?", UUID.class, translator));
  }

  @Test
  void rejectsDanglingReferencesAndDeletingReferencedWork() {
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "INSERT INTO catalog.edition(id,book_id) VALUES (?,?)", UUID.randomUUID(), UUID.randomUUID()));
    UUID work = book("Referenced work");
    jdbc.update("INSERT INTO catalog.edition(id,book_id) VALUES (?,?)", UUID.randomUUID(), work);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM catalog.book WHERE id=?", work));
  }

  @Test
  void validatesLanguagesYearsAndRequiredTitles() {
    UUID work = book("Metadata fixture");
    for (String language : new String[] {"Português", "pt-br", "POR", ""}) {
      assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
          "UPDATE catalog.book SET original_language=? WHERE id=?", language, work));
    }
    jdbc.update("UPDATE catalog.book SET original_language='pt', first_publication_year=-500 WHERE id=?", work);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "UPDATE catalog.book SET first_publication_year=0 WHERE id=?", work));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "UPDATE catalog.book SET canonical_title='   ' WHERE id=?", work));
    UUID person = author("Year fixture");
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "UPDATE catalog.author SET birth_year=1900,death_year=1800 WHERE id=?", person));
  }

  @Test
  void requiresValidMergeTargetAndRejectsUnknownStatus() {
    UUID from = book("Old canonical work");
    UUID target = book("Canonical work");
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book SET status='MERGED' WHERE id=?", from));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book SET status='MERGED',merged_into_id=id WHERE id=?", from));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book SET status='INVALID' WHERE id=?", from));
    jdbc.update("UPDATE catalog.book SET status='MERGED',merged_into_id=? WHERE id=?", target, from);
  }

  @Test
  void creditOrderIsUniqueAndCanBeReorderedInOneTransaction() throws Exception {
    UUID work = book("Two credits");
    UUID first = author("First credit");
    UUID second = author("Second credit");
    jdbc.update("INSERT INTO catalog.book_author VALUES (?,?,'AUTHOR',1)", work, first);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "INSERT INTO catalog.book_author VALUES (?,?,'EDITOR',1)", work, second));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "INSERT INTO catalog.book_author VALUES (?,?,'INVALID',2)", work, second));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "INSERT INTO catalog.book_author VALUES (?,?,'EDITOR',0)", work, second));
    jdbc.update("INSERT INTO catalog.book_author VALUES (?,?,'EDITOR',2)", work, second);
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute("SET CONSTRAINTS catalog.book_author_credit_order DEFERRED");
      try (var update = connection.prepareStatement("UPDATE catalog.book_author SET position=3-position WHERE book_id=?")) {
        update.setObject(1, work);
        update.executeUpdate();
      }
      connection.commit();
    }
    assertEquals(2, jdbc.queryForObject("SELECT position FROM catalog.book_author WHERE book_id=? AND author_id=?", Integer.class, work, first));
  }

  @Test
  void timestampsAreTimezoneAwareAndUpdatesUseDatabaseClock() {
    UUID work = book("Timestamp fixture");
    jdbc.update("UPDATE catalog.book SET description='changed',updated_at='2000-01-01T00:00:00Z' WHERE id=?", work);
    OffsetDateTime created = jdbc.queryForObject("SELECT created_at FROM catalog.book WHERE id=?", OffsetDateTime.class, work);
    OffsetDateTime updated = jdbc.queryForObject("SELECT updated_at FROM catalog.book WHERE id=?", OffsetDateTime.class, work);
    assertFalse(updated.isBefore(created));
    assertEquals("timestamp with time zone", jdbc.queryForObject(
        "SELECT data_type FROM information_schema.columns WHERE table_schema='catalog' AND table_name='book' AND column_name='updated_at'", String.class));
  }

  @Test
  void upgradesFromV1WithoutChangingAppliedChecksums() {
    // A distinct schema inside the disposable test DB simulates an existing V1 install.
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway previous = Flyway.configure().dataSource(dataSource).schemas(schema)
        .defaultSchema(schema).target("1").load();
    try {
      assertEquals(1, previous.migrate().migrationsExecuted);
      Flyway current = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
      assertTrue(current.migrate().migrationsExecuted >= 1);
      current.validate();
      assertEquals(0, current.migrate().migrationsExecuted);
    } finally {
      // Generated identifier contains only a constant prefix and UUID hex.
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }
}
