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
  private final BookPublicationYearQuery publicationYears = org.mockito.Mockito.mock(BookPublicationYearQuery.class);
  private final BookAuthorQuery authors = org.mockito.Mockito.mock(BookAuthorQuery.class);
  @org.junit.jupiter.api.BeforeEach
  void authorsDefault() { when(authors.forBooks(any())).thenReturn(Map.of()); when(publicationYears.forBooks(any())).thenReturn(Map.of()); }
  private final BookCategoryQuery categories = org.mockito.Mockito.mock(BookCategoryQuery.class);
  @Test
  void listsBooksThroughPaginatedRepositoryBoundary() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book();
    book.setId(UUID.randomUUID());
    book.setCanonicalTitle("A Book");
    book.setFirstPublicationYear((short) 1899);
    when(authors.forBooks(List.of(book.getId()))).thenReturn(Map.of(book.getId(), List.of("Machado de Assis", "Coautor")));
    when(categories.forBooks(any())).thenReturn(Map.of());
    when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository, categories, authors, publicationYears).list("", 0, 50);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().canonicalTitle()).isEqualTo("A Book");
    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items().getFirst().publicationYear()).isEqualTo((short) 1899);
    assertThat(result.items().getFirst().authors()).containsExactly("Machado de Assis", "Coautor");
    assertThat(result.items().getFirst().firstPublicationYear()).isEqualTo((short) 1899);
    verify(authors).forBooks(List.of(book.getId()));
  }

  @Test
  void searchesThroughTheBackendInsteadOfSortingInMemory() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book();
    book.setId(UUID.randomUUID());
    book.setCanonicalTitle("Frankenstein");
    when(categories.forBooks(any())).thenReturn(Map.of());
    when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(book)));

    var result = new BookQueryController(repository, categories, authors, publicationYears).list("franken", 2, 20);

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
    var result = new BookQueryController(repository, categories, authors, publicationYears).list("", 0, 50);
    assertThat(result.items().get(0).categories()).containsExactly(category);
    assertThat(result.items().get(1).categories()).isEmpty();
    assertThat(result.items().get(1).authors()).isEmpty();
    assertThat(result.items().get(1).firstPublicationYear()).isNull();
    verify(categories).forBooks(List.of(first.getId(), second.getId()));
  }

  @Test
  void detailUsesTheSameCategoryContractAndMissingBooksStayMissing() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book(); book.setId(UUID.randomUUID()); book.setCanonicalTitle("Book");
    var category = new Category(UUID.randomUUID(), "Fiction", "LCSH");
    when(repository.findById(book.getId())).thenReturn(java.util.Optional.of(book));
    when(categories.forBooks(List.of(book.getId()))).thenReturn(Map.of(book.getId(), List.of(category)));
    book.setFirstPublicationYear((short) 1818);
    when(authors.forBooks(List.of(book.getId()))).thenReturn(Map.of(book.getId(), List.of("Mary Shelley")));
    var controller = new BookQueryController(repository, categories, authors, publicationYears);
    assertThat(controller.get(book.getId()).getBody().categories()).containsExactly(category);
    assertThat(controller.get(book.getId()).getBody().authors()).containsExactly("Mary Shelley");
    assertThat(controller.get(book.getId()).getBody().firstPublicationYear()).isEqualTo((short) 1818);
    assertThat(controller.get(UUID.randomUUID()).getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void exposesEditionYearInListSearchAndDetailWithoutInventingFirstPublication() {
    var repository = org.mockito.Mockito.mock(BookRepository.class);
    var book = new Book(); book.setId(UUID.randomUUID()); book.setCanonicalTitle("Edition only");
    when(repository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(book)));
    when(repository.search(any(), any())).thenReturn(new PageImpl<>(List.of(book)));
    when(repository.findById(book.getId())).thenReturn(java.util.Optional.of(book));
    when(categories.forBooks(any())).thenReturn(Map.of());
    when(publicationYears.forBooks(List.of(book.getId()))).thenReturn(Map.of(book.getId(), (short) 1912));
    var controller = new BookQueryController(repository, categories, authors, publicationYears);
    for (var view : List.of(controller.list("", 0, 50).items().getFirst(),
        controller.list("Edition", 0, 50).items().getFirst(), controller.get(book.getId()).getBody())) {
      assertThat(view.firstPublicationYear()).isNull();
      assertThat(view.publicationYear()).isEqualTo((short) 1912);
    }
    book.setFirstPublicationYear((short) 1818);
    assertThat(controller.get(book.getId()).getBody().publicationYear()).isEqualTo((short) 1818);
  }

}
