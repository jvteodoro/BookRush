package com.bookrush.analytics.api;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps SQL alternatives and transaction-scoped JIT control outside the HTTP layer. */
@Repository
public class BookScoresQueryRepository {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final BookScoresQueryProperties properties;
  private final MeterRegistry metrics;

  public BookScoresQueryRepository(JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Qualifier("transactionManager")
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      BookScoresQueryProperties properties, MeterRegistry metrics) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.properties = properties;
    this.metrics = metrics;
  }

  public QueryResult find(List<UUID> bookIds) {
    String strategy = properties.strategy();
    String jit = properties.jit();
    return transaction.execute(status -> {
      if (jit.equals("ON")) jdbc.execute("SET LOCAL jit = on");
      if (jit.equals("OFF")) jdbc.execute("SET LOCAL jit = off");
      long started = System.nanoTime();
      List<Map<String, Object>> rows = strategy.equals("OPTIMIZED")
          ? optimized(bookIds)
          : current(bookIds);
      metrics.timer("bookrush.analytics.book_scores.query", "strategy", strategy, "jit", jit)
          .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
      metrics.counter("bookrush.analytics.book_scores.query.rows", "strategy", strategy).increment(rows.size());
      return new QueryResult(rows, strategy, jit);
    });
  }

  private List<Map<String, Object>> current(List<UUID> bookIds) {
    String placeholders = String.join(",", Collections.nCopies(bookIds.size(), "?"));
    String sql = """
        WITH latest_asset AS (
          SELECT DISTINCT ON (a.book_id) a.book_id, v.id AS version_id
            FROM catalog.book_asset a
            JOIN catalog.book_asset_version v ON v.id = a.current_version_id
           WHERE a.book_id IN (%s)
             AND a.asset_role IN ('NORMALIZED', 'PROCESSING', 'ANALYTICS')
             AND a.asset_type IN ('TXT', 'HTML')
             AND a.status <> 'DELETED'
             AND v.status = 'AVAILABLE'
           ORDER BY a.book_id, a.updated_at DESC, a.id DESC
        ), ranked AS (
          SELECT la.book_id, er.final_score, er.ranker_version, er.created_at
            FROM latest_asset la
            JOIN analytics.excerpt e ON e.source_asset_version_id = la.version_id
            JOIN LATERAL (
              SELECT final_score, ranker_version, created_at
                FROM analytics.excerpt_rank
               WHERE excerpt_id = e.id AND final_score IS NOT NULL
               ORDER BY created_at DESC
               LIMIT 1
            ) er ON TRUE
           WHERE e.body_eligible = TRUE AND e.exclusion_reason IS NULL
        )
        SELECT book_id, MAX(final_score) AS score,
               (ARRAY_AGG(ranker_version ORDER BY final_score DESC, created_at DESC))[1] AS ranker_version
          FROM ranked GROUP BY book_id
        """.formatted(placeholders);
    return jdbc.queryForList(sql, bookIds.toArray());
  }

  private List<Map<String, Object>> optimized(List<UUID> bookIds) {
    String values = String.join(",", Collections.nCopies(bookIds.size(), "(?)"));
    String sql = """
        WITH requested(book_id) AS (VALUES %s), latest_asset AS (
          SELECT DISTINCT ON (r.book_id) r.book_id, v.id AS version_id
            FROM requested r
            JOIN catalog.book_asset a ON a.book_id = r.book_id
            JOIN catalog.book_asset_version v ON v.id = a.current_version_id
           WHERE a.asset_role IN ('NORMALIZED', 'PROCESSING', 'ANALYTICS')
             AND a.asset_type IN ('TXT', 'HTML')
             AND a.status <> 'DELETED' AND v.status = 'AVAILABLE'
           ORDER BY r.book_id, a.updated_at DESC, a.id DESC
        ), eligible AS (
          SELECT la.book_id, e.id AS excerpt_id
            FROM latest_asset la
            JOIN analytics.excerpt e ON e.source_asset_version_id = la.version_id
           WHERE e.body_eligible = TRUE AND e.exclusion_reason IS NULL
        ), ranked AS (
          SELECT e.book_id, er.final_score, er.ranker_version, er.created_at
            FROM eligible e
            JOIN LATERAL (
              SELECT final_score, ranker_version, created_at
                FROM analytics.excerpt_rank
               WHERE excerpt_id = e.excerpt_id AND final_score IS NOT NULL
               ORDER BY created_at DESC
               LIMIT 1
            ) er ON TRUE
        )
        SELECT book_id, MAX(final_score) AS score,
               (ARRAY_AGG(ranker_version ORDER BY final_score DESC, created_at DESC))[1] AS ranker_version
          FROM ranked GROUP BY book_id
        """.formatted(values);
    return jdbc.queryForList(sql, bookIds.toArray());
  }

  public record QueryResult(List<Map<String, Object>> rows, String strategy, String jit) {}
}
