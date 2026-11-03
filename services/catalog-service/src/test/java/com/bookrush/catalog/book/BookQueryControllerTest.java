package com.bookrush.catalog.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.bookrush.catalog.persistence.model.Book;
import com.bookrush.catalog.subject.BookCategoryQuery;
import com.bookrush.catalog.subject.BookCategoryQuery.Category;
import java.util.Map;
import com.bookrush.catalog.persistence.repository.BookRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class BookQueryControllerTest {
  private final BookCategoryQuery categories = org.mockito.Mockito.mock(BookCategoryQuery.class);
  @Test
  void listsBooksThroughPaginatedRepositoryBoundary() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book();
    book.setId(UUID.randomUUID());
    book.setCanonicalTitle("A Book");
    when(categories.forBooks(any())).thenReturn(Map.of());
    when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository, categories).list("", 0, 50);

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
    when(categories.forBooks(any())).thenReturn(Map.of());
    when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository, categories).list("franken", 2, 20);

    assertThat(result.items()).extracting(BookQueryController.BookView::canonicalTitle)
        .containsExactly("Frankenstein");
    verify(repository).search(org.mockito.ArgumentMatchers.eq("franken"), any(PageRequest.class));
  }
  @Test
  void returnsRealCategoriesInOneBatchAndPreservesUnclassifiedBooks() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var first = new Book(); first.setId(UUID.randomUUID()); first.setCanonicalTitle("Classified");
    var second = new Book(); second.setId(UUID.randomUUID()); second.setCanonicalTitle("Unclassified");
    var category = new Category(UUID.randomUUID(), "Adventure", "LCSH");
    when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(first, second)));
    when(categories.forBooks(List.of(first.getId(), second.getId())))
        .thenReturn(Map.of(first.getId(), List.of(category)));
    var result = new BookQueryController(repository, categories).list("", 0, 50);
    assertThat(result.items().get(0).categories()).containsExactly(category);
    assertThat(result.items().get(1).categories()).isEmpty();
    verify(categories).forBooks(List.of(first.getId(), second.getId()));
  }

  @Test
  void detailUsesTheSameCategoryContractAndMissingBooksStayMissing() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book(); book.setId(UUID.randomUUID()); book.setCanonicalTitle("Book");
    var category = new Category(UUID.randomUUID(), "Fiction", "LCSH");
    when(repository.findById(book.getId())).thenReturn(java.util.Optional.of(book));
    when(categories.forBooks(List.of(book.getId()))).thenReturn(Map.of(book.getId(), List.of(category)));
    var controller = new BookQueryController(repository, categories);
    assertThat(controller.get(book.getId()).getBody().categories()).containsExactly(category);
    assertThat(controller.get(UUID.randomUUID()).getStatusCode().value()).isEqualTo(404);
  }

}
