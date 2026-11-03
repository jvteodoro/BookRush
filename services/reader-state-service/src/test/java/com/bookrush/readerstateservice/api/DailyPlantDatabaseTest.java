package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Runs only against a disposable PostgreSQL database supplied by the test runner. */
@EnabledIfEnvironmentVariable(named = "PLANT_TEST_JDBC_URL", matches = ".+")
class DailyPlantDatabaseTest {
  @Test
  void migratesAndCountsDaysWithoutDuplicatingEvents() {
    var ds = new DriverManagerDataSource(System.getenv("PLANT_TEST_JDBC_URL"), "postgres", "bookrush-test");
    Flyway.configure().dataSource(ds).schemas("reader_state").target("5").load().migrate();
    Flyway.configure().dataSource(ds).schemas("reader_state").load().migrate();
    var jdbc = new JdbcTemplate(ds);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    var service = new DailyPlantService(jdbc);
    var subject = "plant-test-" + UUID.randomUUID();
    var event = UUID.randomUUID();
    tx.execute(status -> service.get(subject));
    for (int i = 0; i < 15; i++) {
      UUID id = i == 0 ? event : UUID.randomUUID();
      tx.execute(status -> service.record(subject, id, 60, Instant.now()));
    }
    var result = tx.execute(status -> service.record(subject, event, 60, Instant.now()));
    assertEquals(900, result.get("todaySeconds"));
    assertEquals(1, result.get("stage"));
    assertTrue((boolean) result.get("goalReached"));
    assertFalse((boolean) result.get("sad"));
    assertEquals(0, tx.execute(status -> service.get("another-user")).get("stage"));

    LocalDate today = LocalDate.now(DailyPlantService.ZONE);
    jdbc.update("UPDATE reader_state.daily_plant SET start_day=? WHERE subject_key=?", today.minusDays(2), subject);
    jdbc.update("UPDATE reader_state.plant_reading_day SET day=? WHERE subject_key=?", today.minusDays(2), subject);
    result = tx.execute(status -> service.get(subject));
    assertEquals(1, result.get("stage"));
    assertTrue((boolean) result.get("sad"));

    var midnightUser = "midnight-" + UUID.randomUUID();
    var beforeMidnight = new DailyPlantService(jdbc, java.time.Clock.fixed(Instant.parse("2026-10-31T02:59:30Z"), java.time.ZoneOffset.UTC));
    var afterMidnight = new DailyPlantService(jdbc, java.time.Clock.fixed(Instant.parse("2026-10-31T03:00:30Z"), java.time.ZoneOffset.UTC));
    tx.execute(status -> beforeMidnight.get(midnightUser));
    var split = tx.execute(status -> afterMidnight.record(midnightUser, UUID.randomUUID(), 60, Instant.parse("2026-10-31T03:00:30Z")));
    assertEquals(30, split.get("todaySeconds"));
    assertEquals(30, jdbc.queryForObject("SELECT active_seconds FROM reader_state.plant_reading_day WHERE subject_key=? AND day=?", Integer.class, midnightUser, LocalDate.parse("2026-10-30")));
  }
}
