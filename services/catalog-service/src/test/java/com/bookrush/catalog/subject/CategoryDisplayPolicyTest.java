package com.bookrush.catalog.subject;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CategoryDisplayPolicyTest {
  @Test
  void hidesRepresentationsIncludingParameterizedMimeTypes() {
    for (String value : new String[]{"application/rdf+xml", "application/epub+zip",
        "application/x-mobipocket-ebook", "image/jpeg", "text/plain; charset=utf-8", "TEXT/HTML"}) {
      assertNull(CategoryDisplayPolicy.displayName(value, "GUTENBERG"));
      assertNull(CategoryDisplayPolicy.displayName(value, "LCSH"));
    }
  }

  @Test
  void hidesLegacyGutenbergTypesAndClassificationCodes() {
    assertNull(CategoryDisplayPolicy.displayName("Text", "GUTENBERG"));
    assertNull(CategoryDisplayPolicy.displayName("PR", "GUTENBERG"));
    assertNull(CategoryDisplayPolicy.displayName("PR123", "LCC"));
    assertNull(CategoryDisplayPolicy.displayName("823.8", "DDC"));
  }

  @Test
  void preservesLiterarySubjectsAndRemovesOnlyTheBookshelfPrefix() {
    for (String value : new String[]{"Art", "Christmas stories", "London (England) -- Fiction",
        "World War II", "Love/War", "Novels", "PR"}) {
      assertEquals(value, CategoryDisplayPolicy.displayName(value, "LCSH"));
    }
    assertEquals("British Literature", CategoryDisplayPolicy.displayName("Category: British Literature", "GUTENBERG"));
    assertEquals("Textbooks", CategoryDisplayPolicy.displayName("Textbooks", "GUTENBERG"));
    assertNull(CategoryDisplayPolicy.displayName("  ", "GUTENBERG"));
  }
}
