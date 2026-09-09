package com.bookrush.catalog.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonicalCommandService {
  private static final String PRINCIPAL = "book-ingestion-service";
  private static final String ENDPOINT = "/api/internal/v1/catalog/commands";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public CanonicalCommandService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  public Map<String, Object> apply(CanonicalCommand command) {
    var cached = findCached(command.operationKey());
    if (cached != null) {
      if (!command.requestHash().equals(cached.requestHash())) {
        throw new CanonicalConflict("operation key was already used with another request hash");
      }
      return cached.body();
    }
    var maximumFence = jdbc.queryForObject(
        "SELECT COALESCE(MAX(fence_token), 0) FROM catalog.ingestion_task WHERE operation_key = ?",
        Long.class, command.operationKey());
    if (maximumFence != null && command.fenceToken() < maximumFence) {
      throw new CanonicalConflict("stale fence token");
    }
    UUID sourceId;
    try {
      sourceId = jdbc.queryForObject("SELECT id FROM catalog.source WHERE code = ?", UUID.class, command.sourceCode());
    } catch (EmptyResultDataAccessException e) {
      throw new CanonicalConflict("unknown source");
    }
    var existing = jdbc.query("""
        SELECT COALESCE(i.book_id, e.book_id) AS book_id
        FROM catalog.external_identifier i
        LEFT JOIN catalog.edition e ON e.id = i.edition_id
        WHERE i.source_id = ? AND i.identifier_type = ? AND i.identifier_value = ?
        """, rs -> rs.next() ? rs.getObject("book_id", UUID.class) : null,
        sourceId, command.identifierType(), command.identifierValue());
    UUID bookId = existing.isEmpty() ? null : existing.get(0);
    String result;
    if (bookId == null) {
      bookId = UUID.randomUUID();
      jdbc.update("""
          INSERT INTO catalog.book(id, canonical_title, original_language, description, status)
          VALUES (?, ?, ?, ?, 'REVIEW_REQUIRED')
          """, bookId, command.title(), command.language(), command.description());
      var identifierId = UUID.randomUUID();
      jdbc.update("""
          INSERT INTO catalog.external_identifier(id, source_id, book_id, identifier_type, identifier_value)
          VALUES (?, ?, ?, ?, ?)
          """, identifierId, sourceId, bookId, command.identifierType(), command.identifierValue());
      result = "CREATED";
    } else {
      jdbc.update("UPDATE catalog.book SET canonical_title = COALESCE(NULLIF(?, ''), canonical_title), description = COALESCE(?, description) WHERE id = ?",
          command.title(), command.description(), bookId);
      result = "UPDATED";
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("result", result);
    body.put("bookId", bookId);
    body.put("operationKey", command.operationKey());
    body.put("fenceToken", command.fenceToken());
    var json = mapper.valueToTree(body);
    jdbc.update("""
        INSERT INTO catalog.ingestion_command_result
          (id, principal, endpoint, idempotency_key, request_hash, response_status, response_body)
        VALUES (?, ?, ?, ?, ?, 200, ?::jsonb)
        """, UUID.randomUUID(), PRINCIPAL, ENDPOINT, command.operationKey(), command.requestHash(), json.toString());
    return body;
  }

  private Cached findCached(String operationKey) {
    var rows = jdbc.query("SELECT request_hash, response_body FROM catalog.ingestion_command_result WHERE principal=? AND endpoint=? AND idempotency_key=?",
        (rs, rowNum) -> new Cached(rs.getString("request_hash"), readBody(rs.getString("response_body"))),
        PRINCIPAL, ENDPOINT, operationKey);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Map<String, Object> readBody(String json) {
    try {
      return mapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored canonical command result is invalid", e);
    }
  }

  private record Cached(String requestHash, Map<String, Object> body) {}

  public static final class CanonicalConflict extends RuntimeException {
    public CanonicalConflict(String message) { super(message); }
  }
}
