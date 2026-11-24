package com.bookrush.catalog.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;

/** Must run on the fresh disposable publishing database, before catalog startup. */
class PublisherIdentifierMigrationIT {
  @Test void publisherIdentifierUpgradePreservesExistingBooks() {
    var source=new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),"fixture","fixture-only");
    var jdbc=new JdbcTemplate(source);
    Flyway.configure().dataSource(source).schemas("catalog").target("17").load().migrate();
    UUID book=UUID.randomUUID(), edition=UUID.randomUUID(), existing=UUID.randomUUID();
    UUID gutenberg=jdbc.queryForObject("SELECT id FROM catalog.source WHERE code='GUTENBERG'",UUID.class);
    UUID uploads=jdbc.queryForObject("SELECT id FROM catalog.source WHERE code='ADMIN_UPLOAD'",UUID.class);
    jdbc.update("INSERT INTO catalog.book(id,canonical_title) VALUES (?,'Existing migration fixture')",book);
    jdbc.update("INSERT INTO catalog.edition(id,book_id) VALUES (?,?)",edition,book);
    jdbc.update("INSERT INTO catalog.external_identifier(id,source_id,edition_id,identifier_type,identifier_value) VALUES (?,?,?,'GUTENBERG_ID','1342')",existing,gutenberg,edition);
    assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO catalog.external_identifier(id,source_id,edition_id,identifier_type,identifier_value) VALUES (?,?,?,'PUBLISHER_SUBMISSION',?)",UUID.randomUUID(),uploads,edition,UUID.randomUUID().toString()));
    var latest=Flyway.configure().dataSource(source).schemas("catalog").load();
    assertEquals(1,latest.migrate().migrationsExecuted);
    latest.validate();
    jdbc.update("INSERT INTO catalog.external_identifier(id,source_id,edition_id,identifier_type,identifier_value) VALUES (?,?,?,'PUBLISHER_SUBMISSION',?)",UUID.randomUUID(),uploads,edition,UUID.randomUUID().toString());
    assertEquals(edition,jdbc.queryForObject("SELECT edition_id FROM catalog.external_identifier WHERE id=?",UUID.class,existing));
    assertEquals(0,latest.migrate().migrationsExecuted);
    assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO catalog.external_identifier(id,source_id,edition_id,identifier_type,identifier_value) VALUES (?,?,?,'PUBLISHER_SUBMISSION','not-a-uuid')",UUID.randomUUID(),uploads,edition));
  }
}
