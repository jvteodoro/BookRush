package com.bookrush.catalog.book;

import com.bookrush.catalog.persistence.model.Book;
import com.bookrush.catalog.persistence.repository.BookRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP boundary for ingestion and analytics consumers. */
@RestController
@RequestMapping("/api/internal/v1/catalog/books")
public class BookQueryController {
  private static final int MAX_PAGE_SIZE = 100;
  private final BookRepository books;

  public BookQueryController(BookRepository books) {
    this.books = books;
  }

  @GetMapping
  public BookPage list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "page must be >= 0 and size must be between 1 and 100");
    }
    Page<Book> result = q.isBlank()
        ? books.findAll(PageRequest.of(page, size, Sort.by("canonicalTitle").ascending()))
        : books.findByCanonicalTitleContainingIgnoreCase(q.trim(),
            PageRequest.of(page, size, Sort.by("canonicalTitle").ascending()));
    return BookPage.from(result);
  }

  @GetMapping("/{bookId}")
  public ResponseEntity<BookView> get(@PathVariable UUID bookId) {
    return books.findById(bookId)
        .map(book -> ResponseEntity.ok(BookView.from(book)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  public record BookPage(java.util.List<BookView> items, int page, int size,
                         long totalItems, int totalPages, boolean last) {
    static BookPage from(Page<Book> page) {
      return new BookPage(page.map(BookView::from).getContent(), page.getNumber(),
          page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
  }

  public record BookView(UUID id, String canonicalTitle, String originalTitle,
                         String originalLanguage, Short firstPublicationYear,
                         String description, String status, Instant createdAt,
                         Instant updatedAt) {
    static BookView from(Book book) {
      return new BookView(book.getId(), book.getCanonicalTitle(), book.getOriginalTitle(),
          book.getOriginalLanguage(), book.getFirstPublicationYear(), book.getDescription(),
          book.getStatus().name(), book.getCreatedAt(), book.getUpdatedAt());
    }
  }
}
