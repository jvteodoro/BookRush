package com.bookrush.catalog.persistence.repository;

import com.bookrush.catalog.persistence.model.Book;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {
  Page<Book> findByCanonicalTitleContainingIgnoreCase(String title, Pageable pageable);

  @Query(value = "SELECT DISTINCT b.* FROM catalog.book b " +
      "LEFT JOIN catalog.book_author ba ON ba.book_id=b.id " +
      "LEFT JOIN catalog.author a ON a.id=ba.author_id " +
      "LEFT JOIN catalog.book_subject bs ON bs.book_id=b.id " +
      "LEFT JOIN catalog.subject sub ON sub.id=bs.subject_id " +
      "WHERE b.canonical_title ILIKE '%' || :q || '%' " +
      "OR b.original_title ILIKE '%' || :q || '%' " +
      "OR b.description ILIKE '%' || :q || '%' " +
      "OR a.name ILIKE '%' || :q || '%' " +
      "OR sub.canonical_name ILIKE '%' || :q || '%' ORDER BY b.canonical_title", nativeQuery = true)
  Page<Book> search(@Param("q") String q, Pageable pageable);

  /** Resolves work-level identifiers and edition-level identifiers to their owning work. */
  @Query(
      value =
          """
          SELECT b.* FROM catalog.external_identifier i
          JOIN catalog.source s ON s.id=i.source_id
          LEFT JOIN catalog.edition e ON e.id=i.edition_id
          JOIN catalog.book b ON b.id=COALESCE(i.book_id,e.book_id)
          WHERE s.code=:source AND i.identifier_type=:type AND i.identifier_value=:value
          """,
      nativeQuery = true)
  Optional<Book> findByExternalIdentifier(
      @Param("source") String source, @Param("type") String type, @Param("value") String value);
}
