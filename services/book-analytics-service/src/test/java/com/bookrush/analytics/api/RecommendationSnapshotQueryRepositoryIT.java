package com.bookrush.analytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_SQL_INTEGRATION", matches = "true")
class RecommendationSnapshotQueryRepositoryIT {
  static {
    System.setProperty(
        "api.version", System.getenv().getOrDefault("TESTCONTAINERS_DOCKER_API_VERSION", "1.40"));
  }

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate jdbc;
  static RecommendationSnapshotQueryRepository repository;
  static UUID book;
  static UUID wrongModelBook;
  static UUID asset;
  static UUID oldVersion;
  static UUID currentVersion;
  static UUID oldExcerpt;
  static UUID currentExcerpt;
  static UUID bgeModel;

  @BeforeAll
  static void schema() {
    DataSource source = dataSource();
    jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE SCHEMA catalog");
    jdbc.execute("CREATE SCHEMA analytics");
    jdbc.execute("CREATE TABLE catalog.book(id uuid PRIMARY KEY)");
    jdbc.execute("""
        CREATE TABLE catalog.book_asset(
          id uuid PRIMARY KEY, book_id uuid NOT NULL, current_version_id uuid,
          asset_role text NOT NULL, asset_type text NOT NULL, status text NOT NULL,
          updated_at timestamptz NOT NULL)
        """);
    jdbc.execute("""
        CREATE TABLE catalog.book_asset_version(
          id uuid PRIMARY KEY, book_asset_id uuid NOT NULL, status text NOT NULL,
          version_number int NOT NULL, created_at timestamptz NOT NULL)
        """);
    jdbc.execute("""
        CREATE TABLE analytics.excerpt(
          id uuid PRIMARY KEY, source_asset_version_id uuid NOT NULL,
          body_eligible boolean NOT NULL, exclusion_reason text)
        """);
    jdbc.execute("""
        CREATE TABLE analytics.excerpt_rank(
          id uuid PRIMARY KEY, excerpt_id uuid NOT NULL, ranker_code text NOT NULL,
          ranker_version text NOT NULL, configuration_hash text NOT NULL,
          final_score double precision, created_at timestamptz NOT NULL)
        """);
    jdbc.execute("""
        CREATE TABLE analytics.embedding_model(
          id uuid PRIMARY KEY, code text NOT NULL, model_version text NOT NULL,
          dimension int NOT NULL)
        """);
    jdbc.execute("""
        CREATE TABLE analytics.document_embedding(
          id uuid PRIMARY KEY, input_asset_version_id uuid NOT NULL,
          embedding_model_id uuid NOT NULL, input_sha256 text NOT NULL,
          dimension int NOT NULL, storage_provider text NOT NULL,
          bucket text, object_key text, created_at timestamptz NOT NULL)
        """);

    book = UUID.randomUUID();
    wrongModelBook = UUID.randomUUID();
    asset = UUID.randomUUID();
    oldVersion = UUID.randomUUID();
    currentVersion = UUID.randomUUID();
    oldExcerpt = UUID.randomUUID();
    currentExcerpt = UUID.randomUUID();
    bgeModel = UUID.randomUUID();
    UUID wrongModel = UUID.randomUUID();
    UUID wrongAsset = UUID.randomUUID();
    UUID wrongVersion = UUID.randomUUID();
    UUID wrongExcerpt = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book VALUES (?), (?)", book, wrongModelBook);
    jdbc.update("INSERT INTO catalog.book_asset VALUES (?, ?, ?, 'NORMALIZED', 'HTML', 'ACTIVE', now())",
        asset, book, currentVersion);
    jdbc.update("INSERT INTO catalog.book_asset VALUES (?, ?, ?, 'NORMALIZED', 'TXT', 'ACTIVE', now())",
        wrongAsset, wrongModelBook, wrongVersion);
    jdbc.update("INSERT INTO catalog.book_asset_version VALUES (?, ?, 'AVAILABLE', 1, now() - interval '2 days'), (?, ?, 'AVAILABLE', 2, now())",
        oldVersion, asset, currentVersion, asset);
    jdbc.update("INSERT INTO catalog.book_asset_version VALUES (?, ?, 'AVAILABLE', 1, now())",
        wrongVersion, wrongAsset);
    jdbc.update("INSERT INTO analytics.excerpt VALUES (?, ?, true, null), (?, ?, true, null)",
        oldExcerpt, oldVersion, currentExcerpt, currentVersion);
    jdbc.update("INSERT INTO analytics.excerpt VALUES (?, ?, true, null)", wrongExcerpt, wrongVersion);
    jdbc.update("INSERT INTO analytics.excerpt_rank VALUES (?, ?, 'ranker', 'old', ?, 0.10, now() - interval '1 day'), (?, ?, 'ranker', 'current', ?, 0.90, now())",
        UUID.randomUUID(), oldExcerpt, "a".repeat(64), UUID.randomUUID(), currentExcerpt, "b".repeat(64));
    jdbc.update("INSERT INTO analytics.excerpt_rank VALUES (?, ?, 'ranker', 'wrong-only', ?, 0.80, now())",
        UUID.randomUUID(), wrongExcerpt, "e".repeat(64));
    jdbc.update("INSERT INTO analytics.embedding_model VALUES (?, 'bge-m3-dense-v1', '2026-01', 1024), (?, 'other-model', '1', 768)",
        bgeModel, wrongModel);
    jdbc.update("INSERT INTO analytics.document_embedding VALUES (?, ?, ?, ?, 1024, 'S3', 'books-ml', 'current.json', now()), (?, ?, ?, ?, 768, 'S3', 'books-ml', 'wrong.json', now())",
        UUID.randomUUID(), currentVersion, bgeModel, "c".repeat(64), UUID.randomUUID(), currentVersion, wrongModel, "d".repeat(64));
    repository = new RecommendationSnapshotQueryRepository(jdbc, "bge-m3-dense-v1", "");
  }

  @Test
  void pinsScoreAndEmbeddingToCatalogCurrentVersion() {
    var result = repository.find(List.of(book, book), 0, 100);
    assertEquals(1, result.requested());
    Map<String, Object> row = result.rows().getFirst();
    assertEquals(currentVersion, row.get("source_asset_version_id"));
    assertEquals(0.90d, ((Number) row.get("score")).doubleValue(), 0.0001);
    assertEquals("current", row.get("ranker_version"));
    assertEquals("bge-m3-dense-v1", row.get("embedding_model"));
    assertEquals(1024, ((Number) row.get("embedding_dimension")).intValue());
    assertNotNull(row.get("embedding_object_key"));
  }

  @Test
  void missingOrWrongModelDoesNotBecomeAZeroEmbedding() {
    var result = repository.find(List.of(wrongModelBook), 0, 100);
    assertEquals(1, result.rows().size());
    Map<String, Object> row = result.rows().getFirst();
    assertNotNull(row.get("source_asset_version_id"));
    assertNull(row.get("embedding_model"));
    assertEquals(0.80d, ((Number) row.get("score")).doubleValue(), 0.0001);
  }

  private static DataSource dataSource() {
    var source = new org.postgresql.ds.PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl());
    source.setUser(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    return source;
  }
}
