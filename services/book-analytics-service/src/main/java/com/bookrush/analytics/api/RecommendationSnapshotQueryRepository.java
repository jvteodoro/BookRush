package com.bookrush.analytics.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads one bounded, version-pinned analytics snapshot for recommendation callers. */
@Repository
public class RecommendationSnapshotQueryRepository {
  private final JdbcTemplate jdbc;
  private final String embeddingModel;
  private final String embeddingModelVersion;

  public RecommendationSnapshotQueryRepository(
      JdbcTemplate jdbc,
      @Value("${analytics.recommendation-snapshot.embedding-model:bge-m3-dense-v1}")
          String embeddingModel,
      @Value("${analytics.recommendation-snapshot.embedding-model-version:}")
          String embeddingModelVersion) {
    this.jdbc = jdbc;
    this.embeddingModel = embeddingModel;
    this.embeddingModelVersion = embeddingModelVersion == null ? "" : embeddingModelVersion;
  }

  public QueryResult find(List<UUID> requestedBookIds, int offset, int limit) {
    var bookIds = requestedBookIds.stream().distinct().toList();
    if (bookIds.isEmpty()) return new QueryResult(List.of(), 0, embeddingModel);
    if (bookIds.size() > 100) throw new IllegalArgumentException("at most 100 book IDs are allowed");

    String values = String.join(",", Collections.nCopies(bookIds.size(), "(?,?)"));
    String sql = """
        WITH requested(book_id, ordinal) AS (VALUES %s), current_assets AS (
          SELECT DISTINCT ON (r.book_id)
                 r.book_id, r.ordinal, a.current_version_id AS source_asset_version_id
            FROM requested r
            JOIN catalog.book_asset a ON a.book_id = r.book_id
            JOIN catalog.book_asset_version v
              ON v.id = a.current_version_id
             AND v.status = 'AVAILABLE'
           WHERE a.current_version_id IS NOT NULL
             AND a.asset_role IN ('NORMALIZED', 'PROCESSING', 'ANALYTICS')
             AND a.asset_type IN ('TXT', 'HTML')
             AND a.status <> 'DELETED'
           ORDER BY r.book_id, a.updated_at DESC NULLS LAST, a.id DESC
        ), score_candidates AS (
          SELECT ca.book_id, er.final_score, er.ranker_code, er.ranker_version,
                 er.configuration_hash, er.created_at,
                 ROW_NUMBER() OVER (
                   PARTITION BY ca.book_id
                   ORDER BY er.final_score DESC, er.created_at DESC, e.id DESC
                 ) AS score_order
            FROM current_assets ca
            JOIN analytics.excerpt e
              ON e.source_asset_version_id = ca.source_asset_version_id
             AND e.body_eligible = TRUE
             AND e.exclusion_reason IS NULL
            JOIN LATERAL (
              SELECT final_score, ranker_code, ranker_version, configuration_hash, created_at
                FROM analytics.excerpt_rank
               WHERE excerpt_id = e.id AND final_score IS NOT NULL
               ORDER BY created_at DESC, id DESC
               LIMIT 1
            ) er ON TRUE
        ), scores AS (
          SELECT book_id, final_score, ranker_code, ranker_version, configuration_hash
            FROM score_candidates WHERE score_order = 1
        )
        SELECT r.book_id,
               ca.source_asset_version_id,
               s.final_score AS score,
               s.ranker_code,
               s.ranker_version,
               s.configuration_hash AS rank_configuration_hash,
               de.code AS embedding_model,
               de.model_version AS embedding_model_version,
               de.dimension AS embedding_dimension,
               de.input_sha256 AS embedding_input_sha256,
               de.storage_provider AS embedding_storage_provider,
               de.bucket AS embedding_bucket,
               de.object_key AS embedding_object_key
          FROM requested r
          LEFT JOIN current_assets ca ON ca.book_id = r.book_id
          LEFT JOIN scores s ON s.book_id = r.book_id
          LEFT JOIN LATERAL (
            SELECT de.*, em.code, em.model_version
              FROM analytics.document_embedding de
              JOIN analytics.embedding_model em ON em.id = de.embedding_model_id
             WHERE de.input_asset_version_id = ca.source_asset_version_id
               AND em.code = ?
               AND (? = '' OR em.model_version = ?)
               AND de.dimension = em.dimension
               AND de.input_sha256 IS NOT NULL
               AND btrim(COALESCE(de.bucket, '')) <> ''
               AND btrim(COALESCE(de.object_key, '')) <> ''
             ORDER BY de.created_at DESC, de.id DESC
             LIMIT 1
          ) de ON TRUE
         ORDER BY r.ordinal
         OFFSET ? LIMIT ?
        """.formatted(values);

    Object[] args = new Object[bookIds.size() * 2 + 5];
    int index = 0;
    for (int ordinal = 0; ordinal < bookIds.size(); ordinal++) {
      args[index++] = bookIds.get(ordinal);
      args[index++] = ordinal;
    }
    args[index++] = embeddingModel;
    args[index++] = embeddingModelVersion;
    args[index++] = embeddingModelVersion;
    args[index++] = Math.max(0, offset);
    args[index] = Math.max(1, Math.min(100, limit));
    return new QueryResult(jdbc.queryForList(sql, args), bookIds.size(), embeddingModel);
  }

  public record QueryResult(List<Map<String, Object>> rows, int requested, String embeddingModel) {}
}
