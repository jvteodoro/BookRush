package com.bookrush.ingestion.catalog;

import java.util.Map;

/** Owns the boundary to catalog-service; ingestion never writes canonical tables directly. */
public interface CanonicalCatalogPort {
  Map<String, Object> apply(CanonicalCatalogCommand command);

  record CanonicalCatalogCommand(String operationKey, String requestHash, long fenceToken,
      String sourceCode, String identifierType, String identifierValue, String title,
      String language, String description, Map<String, Object> metadata,
      String publisher, Integer publicationYear, String authorName, String authorRole) {}
}
