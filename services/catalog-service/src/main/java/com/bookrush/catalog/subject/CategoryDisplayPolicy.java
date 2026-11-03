package com.bookrush.catalog.subject;

import java.util.Locale;
import java.util.regex.Pattern;

/** Filters legacy RDF representation values without deleting source classifications. */
final class CategoryDisplayPolicy {
  private static final Pattern MIME_TYPE = Pattern.compile(
      "^(application|audio|font|image|message|model|multipart|text|video)/[a-z0-9.+_-]+(?:\\s*;.*)?$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern CATEGORY_PREFIX = Pattern.compile("^Category:\\s*", Pattern.CASE_INSENSITIVE);

  static String displayName(String rawName, String rawScheme) {
    String name = CATEGORY_PREFIX.matcher(rawName.trim()).replaceFirst("").trim();
    String scheme = rawScheme.trim().toUpperCase(Locale.ROOT);
    if (MIME_TYPE.matcher(name).matches() || scheme.equals("LCC") || scheme.equals("DDC")) return null;
    if (scheme.equals("GUTENBERG") && (name.equals("Text") || name.matches("[A-Z]{1,3}"))) return null;
    return name.isEmpty() ? null : name;
  }

  private CategoryDisplayPolicy() {}
}
