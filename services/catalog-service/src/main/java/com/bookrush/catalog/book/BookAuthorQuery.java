package com.bookrush.catalog.book;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Loads work authors in credit order with one query per page. */
@Service
public class BookAuthorQuery {
  private final NamedParameterJdbcTemplate jdbc;
  public BookAuthorQuery(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
  public Map<UUID, List<String>> forBooks(List<UUID> ids) {
    Map<UUID, List<String>> result = new HashMap<>();
    if (ids.isEmpty()) return result;
    jdbc.query("""
        SELECT ba.book_id, a.name
        FROM catalog.book_author ba JOIN catalog.author a ON a.id = ba.author_id
        WHERE ba.book_id IN (:books) AND ba.role = 'AUTHOR'
        ORDER BY ba.book_id, ba.position, a.id
        """, Map.of("books", ids), row -> {
          String name = row.getString("name");
          if (name != null && !name.isBlank())
            result.computeIfAbsent(row.getObject("book_id", UUID.class), ignored -> new ArrayList<>()).add(name.trim());
        });
    return result;
  }
}
