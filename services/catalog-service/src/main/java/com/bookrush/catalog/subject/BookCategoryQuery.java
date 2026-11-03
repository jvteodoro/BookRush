package com.bookrush.catalog.subject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads the catalog's existing classifications in one query for a whole book page. */
@Service
public class BookCategoryQuery {
  private final NamedParameterJdbcTemplate jdbc;

  public BookCategoryQuery(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Category(UUID id, String name, String scheme) {}

  public Map<UUID, List<Category>> forBooks(List<UUID> bookIds) {
    Map<UUID, List<Category>> result = new HashMap<>();
    if (bookIds.isEmpty()) return result;
    jdbc.query("""
        SELECT DISTINCT bs.book_id, s.id, s.canonical_name, s.scheme
        FROM catalog.book_subject bs JOIN catalog.subject s ON s.id=bs.subject_id
        WHERE bs.book_id IN (:books) AND s.status='ACTIVE'
        ORDER BY bs.book_id, s.canonical_name, s.scheme, s.id
        """, Map.of("books", bookIds), row -> {
          result.computeIfAbsent(row.getObject("book_id", UUID.class), ignored -> new ArrayList<>())
              .add(new Category(row.getObject("id", UUID.class), row.getString("canonical_name"), row.getString("scheme")));
        });
    return result;
  }
}
