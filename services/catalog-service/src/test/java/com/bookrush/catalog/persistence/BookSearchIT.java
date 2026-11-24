package com.bookrush.catalog.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.catalog.book.BookQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Sql("/search-metadata-fixture.sql")
class BookSearchIT {
  @Autowired BookQueryController books;

  @Test
  void combinesCategoryAuthorYearAndFreeTextWithTheSameCountPredicate() {
    var page = books.list("Harbour", 0, 1, "adventure", "Fixture", 1899);
    assertEquals(1, page.totalItems());
    assertEquals("Alpha Harbour", page.items().getFirst().canonicalTitle());
    assertEquals("Adventure", page.items().getFirst().categories().getFirst().name());
    assertEquals(java.util.List.of("Fixture Writer"), page.items().getFirst().authors());
    assertEquals(0, books.list("Harbour", 0, 20, "Romance", "Fixture", 1899).totalItems());
    assertEquals(2, books.list("Harbour", 0, 1, "", "Fixture", null).totalItems());
    assertEquals("Beta Harbour", books.list("Harbour", 1, 1, "", "Fixture", null).items().getFirst().canonicalTitle());
  }

  @Test
  void retainsYearApiCompatibilityWithoutSearchingYearsInFreeText() {
    assertEquals(0, books.list("1912", 0, 20).totalItems());
    var page = books.list("", 0, 20, "", "", 1912);
    assertEquals(1, page.totalItems());
    var view = page.items().getFirst();
    assertNull(view.firstPublicationYear());
    assertEquals((short) 1912, view.publicationYear());
    assertEquals(1, books.list("", 0, 20, "", "", 1912).totalItems());
    assertEquals(0, books.list("", 0, 20, "", "", 1901).totalItems());
    assertFalse(books.list("1899", 0, 20).items().stream().anyMatch(book -> book.canonicalTitle().equals("Alpha Harbour")));
  }

  @Test
  void onlySearchesVisibleLiteraryCategoriesAndAuthorCredits() {
    for (String value : java.util.List.of("Hidden", "application/rdf+xml", "PR")) {
      assertEquals(0, books.list("", 0, 20, value, "", null).totalItems(), value);
      assertFalse(books.list(value, 0, 100).items().stream()
          .anyMatch(book -> book.canonicalTitle().equals("Gamma Archive")), value);
    }
    assertEquals(0, books.list("", 0, 20, "", "Translator Only", null).totalItems());
    assertEquals(1, books.list("Adventure", 0, 20).totalItems());
    assertEquals(2, books.list("Fixture Writer", 0, 20).totalItems());
  }
}
