package com.bookrush.catalog.persistence;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SourceIdentifierSchemaIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  private UUID source(String code) {
    return jdbc.queryForObject("SELECT id FROM catalog.source WHERE code=?", UUID.class, code);
  }

  private UUID book() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book(id,canonical_title) VALUES (?, 'Pride and Prejudice')", id);
    return id;
  }

  private UUID edition(UUID work) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.edition(id,book_id,language) VALUES (?,?,'en')", id, work);
    return id;
  }

  private void identifier(UUID origin, UUID work, UUID edition, UUID author, String type, String value) {
    jdbc.update("INSERT INTO catalog.external_identifier(id,source_id,book_id,edition_id,author_id,identifier_type,identifier_value) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(), origin, work, edition, author, type, value);
  }

  @Test
  void gutenbergIdentifierResolvesEditionAndWork() {
    UUID work = book();
    UUID manifestation = edition(work);
    identifier(source("GUTENBERG"), null, manifestation, null, "GUTENBERG_ID", "1343");
    UUID found = jdbc.queryForObject("""
        SELECT b.id FROM catalog.external_identifier i
        JOIN catalog.source s ON s.id=i.source_id
        JOIN catalog.edition e ON e.id=i.edition_id
        JOIN catalog.book b ON b.id=e.book_id
        WHERE s.code='GUTENBERG' AND i.identifier_type='GUTENBERG_ID' AND i.identifier_value='1343'
        """, UUID.class);
    assertEquals(work, found);
  }

  @Test
  void requiresExactlyOneExistingTargetAndExistingSource() {
    UUID origin = source("OPEN_LIBRARY");
    UUID work = book();
    UUID manifestation = edition(work);
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, null, null, "WIKIDATA_ID", "Q123"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, work, manifestation, null, "WIKIDATA_ID", "Q123"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, UUID.randomUUID(), null, null, "WIKIDATA_ID", "Q123"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(UUID.randomUUID(), work, null, null, "WIKIDATA_ID", "Q123"));
  }

  @Test
  void enforcesEditionAndWorkIdentifierSemantics() {
    UUID origin = source("OPEN_LIBRARY");
    UUID work = book();
    UUID manifestation = edition(work);
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, work, null, null, "ISBN13", "9780141439518"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, manifestation, null, "OPEN_LIBRARY_WORK_ID", "OL123W"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, work, null, null, "OPEN_LIBRARY_EDITION_ID", "OL123M"));
    identifier(origin, work, null, null, "OPEN_LIBRARY_WORK_ID", "OL123W");
    identifier(origin, null, manifestation, null, "OPEN_LIBRARY_EDITION_ID", "OL123M");
  }

  @Test
  void uniquenessIsScopedToSourceAndType() {
    UUID first = edition(book());
    UUID second = edition(book());
    identifier(source("OPEN_LIBRARY"), null, first, null, "ISBN13", "9780141439518");
    assertThrows(DataIntegrityViolationException.class, () -> identifier(source("OPEN_LIBRARY"), null, second, null, "ISBN13", "9780141439518"));
    identifier(source("ADMIN_UPLOAD"), null, second, null, "ISBN13", "9780141439518");
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM catalog.external_identifier WHERE identifier_type='ISBN13' AND identifier_value='9780141439518'", Integer.class));
  }

  @Test
  void rejectsUnnormalizedIdentifiersAndUnknownTypes() {
    UUID origin = source("ADMIN_UPLOAD");
    UUID manifestation = edition(book());
    for (String invalid : new String[] {"978-0-14-143951-8", "9780141439518 ", "", "abc"}) {
      assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, manifestation, null, "ISBN13", invalid));
    }
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, manifestation, null, "DOI", "10.1234/UPPER"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, manifestation, null, "GUTENBERG_ID", "01343"));
    assertThrows(DataIntegrityViolationException.class, () -> identifier(origin, null, manifestation, null, "OTHER", "123"));
    identifier(origin, null, manifestation, null, "DOI", "10.1234/example");
  }

  @Test
  void licensesDoNotImplicitlyGrantPermissionsAndHaveRealForeignKeys() {
    assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM catalog.license WHERE commercial_use_allowed IS NULL AND modification_allowed IS NULL AND redistribution_allowed IS NULL AND attribution_required IS NULL", Integer.class));
    UUID manifestation = edition(book());
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.edition SET license_id=? WHERE id=?", UUID.randomUUID(), manifestation));
    UUID unknown = jdbc.queryForObject("SELECT id FROM catalog.license WHERE code='UNKNOWN'", UUID.class);
    jdbc.update("UPDATE catalog.edition SET license_id=? WHERE id=?", unknown, manifestation);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM catalog.license WHERE id=?", unknown));
  }

  @Test
  void referenceCodesAreUniqueAndInternalSourcesNeedNoUrl() {
    assertNull(jdbc.queryForObject("SELECT base_url FROM catalog.source WHERE code='INTERNAL_PIPELINE'", String.class));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO catalog.source(id,code,name,source_type) VALUES (?,'GUTENBERG','duplicate','DIGITAL_LIBRARY')", UUID.randomUUID()));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO catalog.license(id,code,name) VALUES (?,'UNKNOWN','duplicate')", UUID.randomUUID()));
  }

  @Test
  void v2DataSurvivesUpgradeToV3() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
      UUID work = UUID.randomUUID();
      UUID manifestation = UUID.randomUUID();
      jdbc.update("INSERT INTO " + schema + ".book(id,canonical_title) VALUES (?,'Existing work')", work);
      jdbc.update("INSERT INTO " + schema + ".edition(id,book_id) VALUES (?,?)", manifestation, work);
      Flyway current = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load();
      assertEquals(1, current.migrate().migrationsExecuted);
      current.validate();
      assertNull(jdbc.queryForObject("SELECT license_id FROM " + schema + ".edition WHERE id=?", UUID.class, manifestation));
      assertEquals(work, jdbc.queryForObject("SELECT book_id FROM " + schema + ".edition WHERE id=?", UUID.class, manifestation));
    } finally {
      jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }
}
