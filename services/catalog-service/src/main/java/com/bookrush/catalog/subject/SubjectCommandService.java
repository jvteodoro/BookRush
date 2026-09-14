package com.bookrush.catalog.subject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog owner for idempotent, source-aware bibliographic subject assignments. */
@Service
public class SubjectCommandService {
  private final JdbcTemplate jdbc;
  public SubjectCommandService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public Map<String, Object> assign(SubjectCommand command) {
    var scheme = command.scheme().trim().toUpperCase(Locale.ROOT);
    var normalized = normalize(command.term());
    UUID sourceId;
    try { sourceId = jdbc.queryForObject("SELECT id FROM catalog.source WHERE code=? AND is_active", UUID.class, command.sourceCode().trim().toUpperCase(Locale.ROOT)); }
    catch (EmptyResultDataAccessException e) { throw new SubjectConflict("unknown source"); }
    if (command.sourceRecordId() != null && jdbc.query("SELECT id FROM catalog.source_record WHERE id=? AND source_id=?", (rs, row) -> rs.getObject(1), command.sourceRecordId(), sourceId).isEmpty()) {
      throw new SubjectConflict("source record does not belong to source");
    }
    var subjectId = jdbc.queryForObject("INSERT INTO catalog.subject(id,canonical_name,normalized_name,scheme) VALUES (?,?,?,?) ON CONFLICT (scheme,normalized_name) DO UPDATE SET updated_at=CURRENT_TIMESTAMP RETURNING id", UUID.class, UUID.randomUUID(), command.term().trim(), normalized, scheme);
    if (jdbc.query("SELECT 1 FROM catalog.book WHERE id=?", (rs, row) -> rs.getInt(1), command.bookId()).isEmpty()) throw new SubjectConflict("book not found");
    var inserted = jdbc.update("INSERT INTO catalog.book_subject(book_id,subject_id,source_id,source_record_id,confidence,assignment_method) VALUES (?,?,?,?,?,?) ON CONFLICT (book_id,subject_id,source_id) DO NOTHING", command.bookId(), subjectId, sourceId, command.sourceRecordId(), command.confidence(), command.assignmentMethod());
    return Map.of("result", inserted == 1 ? "CREATED" : "NOOP", "bookId", command.bookId(), "subjectId", subjectId, "scheme", scheme, "normalizedName", normalized);
  }

  static String normalize(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("term is required");
    return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
  public record SubjectCommand(UUID bookId, String scheme, String term, String sourceCode, UUID sourceRecordId, java.math.BigDecimal confidence, String assignmentMethod) {}
  public static final class SubjectConflict extends RuntimeException { public SubjectConflict(String message) { super(message); } }
}
