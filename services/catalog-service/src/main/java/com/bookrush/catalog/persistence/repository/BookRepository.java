package com.bookrush.catalog.persistence.repository;

import com.bookrush.catalog.persistence.model.Book;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {
  /** Resolves work-level identifiers and edition-level identifiers to their owning work. */
  @Query(value = """
      SELECT b.* FROM catalog.external_identifier i
      JOIN catalog.source s ON s.id=i.source_id
      LEFT JOIN catalog.edition e ON e.id=i.edition_id
      JOIN catalog.book b ON b.id=COALESCE(i.book_id,e.book_id)
      WHERE s.code=:source AND i.identifier_type=:type AND i.identifier_value=:value
      """, nativeQuery = true)
  Optional<Book> findByExternalIdentifier(@Param("source") String source,
      @Param("type") String type, @Param("value") String value);
}
