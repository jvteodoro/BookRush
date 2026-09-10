package com.bookrush.catalog.canonical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional owner of the bibliographic projection used by ingestion workers. */
@Service
public class CanonicalCommandService {
  private static final String PRINCIPAL = "book-ingestion-service";
  private static final String ENDPOINT = "/api/internal/v1/catalog/commands";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public CanonicalCommandService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

  @Transactional
  public Map<String, Object> apply(CanonicalCommand command) {
    var cached = findCached(command.operationKey());
    if (cached != null) {
      if (!command.requestHash().equals(cached.requestHash())) throw new CanonicalConflict("operation key was already used with another request hash");
      return cached.body();
    }
    var maximumFence = jdbc.queryForObject("SELECT COALESCE(MAX(fence_token), 0) FROM catalog.ingestion_task WHERE operation_key = ?", Long.class, command.operationKey());
    if (maximumFence != null && command.fenceToken() < maximumFence) throw new CanonicalConflict("stale fence token");
    UUID sourceId;
    try { sourceId = jdbc.queryForObject("SELECT id FROM catalog.source WHERE code = ? AND is_active", UUID.class, command.sourceCode().toUpperCase(Locale.ROOT)); }
    catch (EmptyResultDataAccessException e) { throw new CanonicalConflict("unknown source"); }
    var identifierType = command.identifierType().toUpperCase(Locale.ROOT);
    var identifierValue = command.identifierValue().trim();
    var existing = jdbc.query("SELECT COALESCE(i.book_id, e.book_id) AS book_id, i.edition_id FROM catalog.external_identifier i LEFT JOIN catalog.edition e ON e.id=i.edition_id WHERE i.source_id=? AND i.identifier_type=? AND i.identifier_value=?", (rs, row) -> new Target(rs.getObject("book_id", UUID.class), rs.getObject("edition_id", UUID.class)), sourceId, identifierType, identifierValue).stream().findFirst().orElse(null);
    UUID bookId = existing == null ? null : existing.bookId();
    UUID editionId = existing == null ? null : existing.editionId();
    String result;
    if (bookId == null) {
      bookId = UUID.randomUUID(); editionId = UUID.randomUUID();
      jdbc.update("INSERT INTO catalog.book(id, canonical_title, original_title, original_language, description, status) VALUES (?, ?, ?, ?, ?, 'REVIEW_REQUIRED')", bookId, command.title().trim(), command.title().trim(), normalizeLanguage(command.language()), command.description());
      jdbc.update("INSERT INTO catalog.edition(id, book_id, title, language, publisher, publication_year, description) VALUES (?, ?, ?, ?, ?, ?, ?)", editionId, bookId, command.title().trim(), normalizeLanguage(command.language()), command.publisher(), command.publicationYear(), command.description());
      jdbc.update("INSERT INTO catalog.external_identifier(id, source_id, edition_id, identifier_type, identifier_value) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), sourceId, editionId, identifierType, identifierValue);
      result = "CREATED";
    } else {
      var changed = jdbc.update("UPDATE catalog.book SET canonical_title=COALESCE(NULLIF(?, ''), canonical_title), description=COALESCE(?, description) WHERE id=?", command.title(), command.description(), bookId);
      changed += jdbc.update("UPDATE catalog.edition SET title=COALESCE(NULLIF(?, ''), title), language=COALESCE(?, language), publisher=COALESCE(?, publisher), publication_year=COALESCE(?, publication_year), description=COALESCE(?, description) WHERE id=? AND book_id=?", command.title(), normalizeLanguage(command.language()), command.publisher(), command.publicationYear(), command.description(), editionId, bookId);
      result = changed == 0 ? "NOOP" : "UPDATED";
    }
    var authorIds = new ArrayList<UUID>();
    if (command.authorName() != null && !command.authorName().isBlank()) {
      var normalized = normalizeName(command.authorName());
      var author = jdbc.query("SELECT id FROM catalog.author WHERE normalized_name=?", (rs, row) -> rs.getObject("id", UUID.class), normalized).stream().findFirst().orElse(null);
      var authorId = author == null ? UUID.randomUUID() : author;
      if (author == null) jdbc.update("INSERT INTO catalog.author(id, name, normalized_name) VALUES (?, ?, ?)", authorId, command.authorName().trim(), normalized);
      var role = command.authorRole() == null || command.authorRole().isBlank() ? "AUTHOR" : command.authorRole().toUpperCase(Locale.ROOT);
      var exists = jdbc.queryForObject("SELECT count(*) FROM catalog.edition_author WHERE edition_id=? AND author_id=? AND role=?", Integer.class, editionId, authorId, role);
      if (exists == 0) {
        var position = jdbc.queryForObject("SELECT COALESCE(max(position),0)+1 FROM catalog.edition_author WHERE edition_id=?", Integer.class, editionId);
        jdbc.update("INSERT INTO catalog.edition_author(edition_id, author_id, role, position) VALUES (?, ?, ?, ?)", editionId, authorId, role, position);
      }
      var bookCredit = jdbc.queryForObject("SELECT count(*) FROM catalog.book_author WHERE book_id=? AND author_id=? AND role=?", Integer.class, bookId, authorId, role);
      if (bookCredit == 0) {
        var position = jdbc.queryForObject("SELECT COALESCE(max(position),0)+1 FROM catalog.book_author WHERE book_id=?", Integer.class, bookId);
        jdbc.update("INSERT INTO catalog.book_author(book_id, author_id, role, position) VALUES (?, ?, ?, ?)", bookId, authorId, role, position);
      }
      authorIds.add(authorId);
    }
    recordProvenance(sourceId, bookId, "canonical_title", command.title());
    var body = new LinkedHashMap<String, Object>();
    body.put("result", result); body.put("bookId", bookId); body.put("editionId", editionId); body.put("authorIds", authorIds); body.put("operationKey", command.operationKey()); body.put("fenceToken", command.fenceToken());
    jdbc.update("INSERT INTO catalog.ingestion_command_result(id, principal, endpoint, idempotency_key, request_hash, response_status, response_body) VALUES (?, ?, ?, ?, ?, 200, ?::jsonb)", UUID.randomUUID(), PRINCIPAL, ENDPOINT, command.operationKey(), command.requestHash(), mapper.valueToTree(body).toString());
    return body;
  }

  private void recordProvenance(UUID sourceId, UUID entityId, String field, String value) {
    jdbc.update("INSERT INTO catalog.field_provenance(id, entity_type, entity_id, field_name, value, source_id, rule_code, actor) VALUES (?, 'BOOK', ?, ?, ?::jsonb, ?, ?, ?)", UUID.randomUUID(), entityId, field, mapper.valueToTree(value).toString(), sourceId, "GUTENBERG_CANONICAL", PRINCIPAL);
  }
  private String normalizeLanguage(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
  private String normalizeName(String value) { return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
  private Cached findCached(String operationKey) {
    var rows = jdbc.query("SELECT request_hash, response_body::text AS response_body FROM catalog.ingestion_command_result WHERE principal=? AND endpoint=? AND idempotency_key=?", (rs, row) -> new Cached(rs.getString("request_hash"), readBody(rs.getString("response_body"))), PRINCIPAL, ENDPOINT, operationKey);
    return rows.isEmpty() ? null : rows.getFirst();
  }
  @SuppressWarnings("unchecked") private Map<String, Object> readBody(String json) { try { return mapper.readValue(json, Map.class); } catch (JsonProcessingException e) { throw new IllegalStateException("stored canonical command result is invalid", e); } }
  private record Cached(String requestHash, Map<String, Object> body) {}
  private record Target(UUID bookId, UUID editionId) {}
  public static final class CanonicalConflict extends RuntimeException { public CanonicalConflict(String message) { super(message); } }
}
