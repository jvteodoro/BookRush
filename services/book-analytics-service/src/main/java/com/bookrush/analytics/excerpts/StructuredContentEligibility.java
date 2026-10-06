package com.bookrush.analytics.excerpts;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies structural contamination at the analytics boundary. Recommendation
 * consumes the persisted result and never reimplements these editorial rules.
 */
public final class StructuredContentEligibility {
  private static final Pattern TOC_LINE = Pattern.compile(
      "(?im)^\\s*(?:letter|chapter|book|part|volume)\\s+[ivxlcdm\\d]+(?:\\s+.*)?(?:\\.{2,}|\\s{2,}|\\d+\\s*)$");
  private static final Pattern TOC_HEADER = Pattern.compile("(?im)^\\s*(?:contents|table\\s+of\\s+contents)\\s*$");
  private static final Pattern BOILERPLATE = Pattern.compile("(?is)\\b(?:project gutenberg|end of the project gutenberg ebook)\\b");

  private StructuredContentEligibility() {}

  public static Decision classify(String text) {
    if (text == null || text.isBlank()) return new Decision(false, "MALFORMED_TEXT");
    String normalized = text.trim();
    if (BOILERPLATE.matcher(normalized).find()) return new Decision(false, "BOILERPLATE");
    if (TOC_HEADER.matcher(normalized).find() || tocSequence(normalized)) {
      return new Decision(false, "TABLE_OF_CONTENTS");
    }
    if (headingOnly(normalized)) return new Decision(false, "HEADING_ONLY");
    return new Decision(true, null);
  }

  private static boolean tocSequence(String text) {
    String[] lines = text.split("\\R");
    int matches = 0;
    for (String line : lines) if (TOC_LINE.matcher(line).matches()) matches++;
    return matches >= 3;
  }

  private static boolean headingOnly(String text) {
    String oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() < 120 && oneLine.split(" ").length <= 12
        && oneLine.toUpperCase(Locale.ROOT).equals(oneLine)
        && !oneLine.endsWith(".") && !oneLine.endsWith("!") && !oneLine.endsWith("?");
  }

  public record Decision(boolean bodyEligible, String exclusionReason) {}
}
