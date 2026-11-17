package com.bookrush.catalog.book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Earliest known edition year; never claims to be the work's first publication. */
@Service
public class BookPublicationYearQuery {
  private final NamedParameterJdbcTemplate jdbc;

  public BookPublicationYearQuery(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<UUID, Short> forBooks(List<UUID> ids) {
    Map<UUID, Short> result = new HashMap<>();
    if (ids.isEmpty()) return result;
    jdbc.query("""
        SELECT book_id, min(publication_year) AS publication_year
        FROM catalog.edition
        WHERE book_id IN (:books) AND publication_year IS NOT NULL
        GROUP BY book_id
        """, Map.of("books", ids), row -> {
          result.put(row.getObject("book_id", UUID.class), row.getShort("publication_year"));
        });
    return result;
  }
}
