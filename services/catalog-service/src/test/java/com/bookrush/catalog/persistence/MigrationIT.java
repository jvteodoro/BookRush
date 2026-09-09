package com.bookrush.catalog.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only with -Pintegration-tests against a disposable real PostgreSQL. */
@SpringBootTest
class MigrationIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;

  @Test
  void bootMigratesAndRepeatedMigrationIsANoOp() {
    assertNotNull(flyway.info().current());
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertEquals(0, jdbc.queryForObject(
        "SELECT count(*) FROM catalog.flyway_schema_history WHERE NOT success", Integer.class));
    assertNotNull(jdbc.queryForObject("SELECT to_regprocedure('catalog.set_updated_at()')::text", String.class));
  }

  @Test
  void hibernateStartsAfterFlywayWithoutEnablingDestructiveClean() {
    assertTrue(flyway.getConfiguration().isCleanDisabled());
    assertFalse(flyway.getConfiguration().isBaselineOnMigrate());
    assertEquals("catalog", flyway.getConfiguration().getDefaultSchema());
    assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
  }
}
