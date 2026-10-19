package com.bookrush.analytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class BookScoresQueryRepositoryIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static JdbcTemplate jdbc;

  @BeforeAll
  static void schema() {
    jdbc = new JdbcTemplate(dataSource());
    jdbc.execute("CREATE SCHEMA catalog");
    jdbc.execute("CREATE SCHEMA analytics");
    jdbc.execute("CREATE TABLE catalog.book(id uuid PRIMARY KEY)");
    jdbc.execute("CREATE TABLE catalog.book_asset(id uuid PRIMARY KEY, book_id uuid NOT NULL, current_version_id uuid, asset_role text, asset_type text, status text, updated_at timestamptz)");
    jdbc.execute("CREATE TABLE catalog.book_asset_version(id uuid PRIMARY KEY, book_asset_id uuid NOT NULL, status text, version_number int, created_at timestamptz)");
    jdbc.execute("CREATE TABLE analytics.excerpt(id uuid PRIMARY KEY, source_asset_version_id uuid NOT NULL, body_eligible boolean, exclusion_reason text)");
    jdbc.execute("CREATE TABLE analytics.excerpt_rank(id uuid PRIMARY KEY, excerpt_id uuid NOT NULL, ranker_version text, created_at timestamptz, final_score double precision)");
    UUID bookA = UUID.randomUUID();
    UUID bookB = UUID.randomUUID();
    UUID assetA = UUID.randomUUID();
    UUID assetB = UUID.randomUUID();
    UUID versionA = UUID.randomUUID();
    UUID versionB = UUID.randomUUID();
    UUID excerptA = UUID.randomUUID();
    UUID excerptB = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book VALUES (?), (?)", bookA, bookB);
    jdbc.update("INSERT INTO catalog.book_asset VALUES (?, ?, ?, 'NORMALIZED', 'HTML', 'AVAILABLE', clock_timestamp()), (?, ?, ?, 'NORMALIZED', 'TXT', 'AVAILABLE', clock_timestamp())",
        assetA, bookA, versionA, assetB, bookB, versionB);
    jdbc.update("INSERT INTO catalog.book_asset_version VALUES (?, ?, 'AVAILABLE', 1, clock_timestamp()), (?, ?, 'AVAILABLE', 1, clock_timestamp())",
        versionA, assetA, versionB, assetB);
    jdbc.update("INSERT INTO analytics.excerpt VALUES (?, ?, true, null), (?, ?, true, null)", excerptA, versionA, excerptB, versionB);
    jdbc.update("INSERT INTO analytics.excerpt_rank VALUES (?, ?, 'v1', now() - interval '1 minute', 0.70), (?, ?, 'v2', now(), 0.90), (?, ?, 'v1', now(), null)",
        UUID.randomUUID(), excerptA, UUID.randomUUID(), excerptA, UUID.randomUUID(), excerptB);
    POSTGRES.getContainerInfo();
  }

  @Test
  void currentAndOptimizedQueriesAreEquivalentAndJitSettingDoesNotLeak() {
    var current = repository("CURRENT", "OFF").find(bookIds());
    var optimized = repository("OPTIMIZED", "OFF").find(bookIds());
    assertEquals(normalize(current.rows()), normalize(optimized.rows()));
    assertEquals("OFF", current.jit());
    assertEquals("OFF", optimized.jit());
    assertTrue(jdbc.queryForObject("SHOW jit", String.class) != null);
  }

  private BookScoresQueryRepository repository(String strategy, String jit) {
    DataSource dataSource = dataSource();
    return new BookScoresQueryRepository(jdbc, new DataSourceTransactionManager(dataSource),
        new BookScoresQueryProperties(strategy, jit), new SimpleMeterRegistry());
  }

  private static DataSource dataSource() {
    org.postgresql.ds.PGSimpleDataSource source = new org.postgresql.ds.PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl());
    source.setUser(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    return source;
  }

  private static List<UUID> bookIds() {
    return jdbc.query("SELECT id FROM catalog.book ORDER BY id", (rs, n) -> rs.getObject(1, UUID.class));
  }

  private static List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
    return rows.stream().map(row -> Map.of(
        "book_id", row.get("book_id"),
        "score", row.get("score"),
        "ranker_version", row.get("ranker_version"))).toList();
  }
}
