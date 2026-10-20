package com.bookrush.analytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class BookScoresQueryRepositoryIT {
  static {
    // docker-java otherwise defaults to API 1.32, rejected by current daemons.
    System.setProperty("api.version",
        System.getenv().getOrDefault("TESTCONTAINERS_DOCKER_API_VERSION", "1.40"));
  }

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static JdbcTemplate jdbc;
  static DataSource sharedDataSource;
  static Flyway flyway;
  static String baselineJit;

  @BeforeAll
  static void schema() {
    sharedDataSource = dataSource();
    jdbc = new JdbcTemplate(sharedDataSource);
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
    UUID bookWithoutRank = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book VALUES (?), (?), (?)", bookA, bookB, bookWithoutRank);
    jdbc.update("INSERT INTO catalog.book_asset VALUES (?, ?, ?, 'NORMALIZED', 'HTML', 'AVAILABLE', clock_timestamp()), (?, ?, ?, 'NORMALIZED', 'TXT', 'AVAILABLE', clock_timestamp())",
        assetA, bookA, versionA, assetB, bookB, versionB);
    jdbc.update("INSERT INTO catalog.book_asset_version VALUES (?, ?, 'AVAILABLE', 1, clock_timestamp()), (?, ?, 'AVAILABLE', 1, clock_timestamp())",
        versionA, assetA, versionB, assetB);
    jdbc.update("INSERT INTO analytics.excerpt VALUES (?, ?, true, null), (?, ?, true, null)", excerptA, versionA, excerptB, versionB);
    jdbc.update("INSERT INTO analytics.excerpt_rank VALUES (?, ?, 'v1', now() - interval '1 minute', 0.70), (?, ?, 'v2', now(), 0.90), (?, ?, 'v1', now(), null)",
        UUID.randomUUID(), excerptA, UUID.randomUUID(), excerptA, UUID.randomUUID(), excerptB);
    baselineJit = jdbc.queryForObject("SHOW jit", String.class);
    flyway = Flyway.configure()
        .dataSource(sharedDataSource)
        .schemas("analytics")
        .defaultSchema("analytics")
        .baselineOnMigrate(true)
        .baselineVersion("27")
        .executeInTransaction(false)
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .locations("classpath:db/migration")
        .load();
    flyway.migrate();
  }

  @Test
  void currentAndOptimizedQueriesAreEquivalentAndJitSettingDoesNotLeak() {
    var requested = bookIds();
    requested.add(requested.get(0));
    var current = repository("CURRENT", "OFF").find(requested);
    var optimized = repository("OPTIMIZED", "OFF").find(requested);
    assertEquals(normalize(current.rows()), normalize(optimized.rows()));
    assertEquals("OFF", current.jit());
    assertEquals("OFF", optimized.jit());
    assertEquals(baselineJit, jdbc.queryForObject("SHOW jit", String.class));
    assertTrue(current.rows().stream().noneMatch(row -> row.get("book_id") == null));
    assertIndex("excerpt_rank_latest_score_idx", "excerpt_id, created_at DESC", "final_score IS NOT NULL");
    assertIndex("excerpt_eligible_asset_version_idx", "source_asset_version_id, id", "body_eligible = true AND exclusion_reason IS NULL");
  }

  @Test
  void localJitUsesTheTransactionConnectionAndDoesNotLeakAfterCommitOrRollback() {
    var transaction = new org.springframework.transaction.support.TransactionTemplate(
        new DataSourceTransactionManager(sharedDataSource));
    String[] pids = new String[2];
    transaction.executeWithoutResult(status -> {
      jdbc.execute("SET LOCAL jit = off");
      pids[0] = jdbc.queryForObject("SELECT pg_backend_pid()", String.class);
      assertEquals("off", jdbc.queryForObject("SHOW jit", String.class));
      pids[1] = jdbc.queryForObject("SELECT pg_backend_pid()", String.class);
    });
    assertEquals(pids[0], pids[1]);
    assertEquals(baselineJit, jdbc.queryForObject("SHOW jit", String.class));

    try {
      transaction.executeWithoutResult(status -> {
        jdbc.execute("SET LOCAL jit = off");
        assertEquals("off", jdbc.queryForObject("SHOW jit", String.class));
        throw new IllegalStateException("rollback probe");
      });
    } catch (IllegalStateException expected) {
      // The transaction must be rolled back without changing the pool default.
    }
    assertEquals(baselineJit, jdbc.queryForObject("SHOW jit", String.class));
  }

  @Test
  void definitiveIndexMigrationsAreNoOpWhenValidatedAgain() {
    flyway.migrate();
    assertEquals(2, jdbc.queryForObject(
        "SELECT count(*) FROM analytics.flyway_schema_history WHERE version IN ('28', '29')", Integer.class));
  }

  private BookScoresQueryRepository repository(String strategy, String jit) {
    return new BookScoresQueryRepository(jdbc, new DataSourceTransactionManager(sharedDataSource),
        new BookScoresQueryProperties(strategy, jit), new SimpleMeterRegistry());
  }

  private void assertIndex(String name, String columns, String predicate) {
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT indexdef, indisvalid FROM pg_indexes i JOIN pg_class c ON c.relname=i.indexname "
            + "JOIN pg_index x ON x.indexrelid=c.oid WHERE i.schemaname='analytics' AND i.indexname=?", name);
    assertNotNull(row.get("indexdef"));
    assertTrue(String.valueOf(row.get("indexdef")).contains(columns));
    String normalizedDefinition = String.valueOf(row.get("indexdef")).toLowerCase()
        .replace("(", "").replace(")", "");
    assertTrue(normalizedDefinition.contains(predicate.toLowerCase()));
    assertEquals(true, row.get("indisvalid"));
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
