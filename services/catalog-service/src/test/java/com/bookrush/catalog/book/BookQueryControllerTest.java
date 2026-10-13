package com.bookrush.catalog.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.bookrush.catalog.persistence.model.Book;
import com.bookrush.catalog.persistence.repository.BookRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class BookQueryControllerTest {
  @Test
  void listsBooksThroughPaginatedRepositoryBoundary() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book();
    book.setId(UUID.randomUUID());
    book.setCanonicalTitle("A Book");
    when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository).list("", 0, 50);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().canonicalTitle()).isEqualTo("A Book");
    assertThat(result.totalItems()).isEqualTo(1);
  }

  @Test
  void searchesThroughTheBackendInsteadOfSortingInMemory() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book();
    book.setId(UUID.randomUUID());
    book.setCanonicalTitle("Frankenstein");
    when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository).list("franken", 2, 20);

    assertThat(result.items()).extracting(BookQueryController.BookView::canonicalTitle)
        .containsExactly("Frankenstein");
    verify(repository).search(org.mockito.ArgumentMatchers.eq("franken"), any(PageRequest.class));
  }
}
