package com.bookrush.catalog.storage;

import java.util.Locale;
import java.util.UUID;
import com.bookrush.catalog.persistence.model.BookAssetAssetRole;

/** Keys include logical asset identity to avoid collisions between equal names/types in the same work. */
public final class ObjectKeyBuilder {
  private ObjectKeyBuilder() {}
  public static String filename(String name) {
    if (name==null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}") || name.contains(".."))
      throw new IllegalArgumentException("Filename must be 1–120 ASCII letters, digits, dot, underscore or hyphen; no paths");
    return name;
  }
  /** Returns books/{bookId}/{role}/{assetId}/v{number}/{filename}; never takes a title or raw path. */
  public static String build(UUID bookId, UUID assetId, BookAssetAssetRole role, int version, String filename) {
    if (bookId==null || assetId==null || role==null || version<1) throw new IllegalArgumentException("Invalid object identity");
    return "books/"+bookId+"/"+role.name().toLowerCase(Locale.ROOT)+"/"+assetId+"/v"+version+"/"+filename(filename);
  }
}
