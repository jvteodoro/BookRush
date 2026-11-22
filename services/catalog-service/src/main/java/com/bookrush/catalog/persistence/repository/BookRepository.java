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

  String EFFECTIVE_YEAR = "COALESCE(b.first_publication_year, (SELECT min(e.publication_year) FROM catalog.edition e WHERE e.book_id=b.id))";
  String CATEGORY_NAME = "btrim(regexp_replace(btrim(sub.canonical_name), '^Category:[[:space:]]*', '', 'i'))";
  String VISIBLE_CATEGORY = "sub.status='ACTIVE' AND upper(sub.scheme) NOT IN ('LCC','DDC') AND "
      + CATEGORY_NAME + " !~* '^(application|audio|font|image|message|model|multipart|text|video)/' AND NOT (upper(sub.scheme)='GUTENBERG' AND ("
      + CATEGORY_NAME + "='Text' OR " + CATEGORY_NAME + " ~ '^[A-Z]{1,3}$'))";
  String FILTERS = """
      (:q='' OR
        to_tsvector('simple', coalesce(b.canonical_title,'') || ' ' || coalesce(b.original_title,'') || ' ' || coalesce(b.description,''))
          @@ websearch_to_tsquery('simple', :q)
        OR b.canonical_title ILIKE '%' || :q || '%'
        OR b.original_title ILIKE '%' || :q || '%'
        OR b.description ILIKE '%' || :q || '%'
        OR EXISTS (SELECT 1 FROM catalog.book_author ba JOIN catalog.author a ON a.id=ba.author_id
                   WHERE ba.book_id=b.id AND ba.role='AUTHOR' AND a.name ILIKE '%' || :q || '%')
        OR EXISTS (SELECT 1 FROM catalog.book_subject bs JOIN catalog.subject sub ON sub.id=bs.subject_id
                   WHERE bs.book_id=b.id AND
      """ + VISIBLE_CATEGORY + " AND " + CATEGORY_NAME + " ILIKE '%' || :q || '%') OR CAST(" + EFFECTIVE_YEAR + " AS text)=:q)" + """
      AND (:category='' OR EXISTS (SELECT 1 FROM catalog.book_subject bs JOIN catalog.subject sub ON sub.id=bs.subject_id
                                  WHERE bs.book_id=b.id AND
      """ + VISIBLE_CATEGORY + " AND " + CATEGORY_NAME + " ILIKE '%' || :category || '%'))" + """
      AND (:author='' OR EXISTS (SELECT 1 FROM catalog.book_author ba JOIN catalog.author a ON a.id=ba.author_id
                                WHERE ba.book_id=b.id AND ba.role='AUTHOR' AND a.name ILIKE '%' || :author || '%'))
      AND (CAST(:year AS integer) IS NULL OR
      """ + EFFECTIVE_YEAR + "=CAST(:year AS integer))";

  @Query(value = "SELECT b.* FROM catalog.book b WHERE " + FILTERS + """
      ORDER BY CASE WHEN :q='' THEN 0 ELSE ts_rank_cd(
        to_tsvector('simple', coalesce(b.canonical_title,'') || ' ' || coalesce(b.original_title,'') || ' ' || coalesce(b.description,'')),
        websearch_to_tsquery('simple', :q)) END DESC, lower(b.canonical_title), b.id
      """, countQuery = "SELECT count(*) FROM catalog.book b WHERE " + FILTERS, nativeQuery = true)
  Page<Book> searchFiltered(@Param("q") String q, @Param("category") String category,
      @Param("author") String author, @Param("year") Integer year, Pageable pageable);

  default Page<Book> search(String q, Pageable pageable) {
    return searchFiltered(q, "", "", null, pageable);
  }

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
