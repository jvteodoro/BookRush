package com.bookrush.ingestion.processing;

import java.text.Normalizer;

public final class PlainTextNormalizer {
  public String normalize(String input) {
    if (input == null) return "";
    var value = input.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
    value = Normalizer.normalize(value, Normalizer.Form.NFC);
    value = value.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\\n\\n");
    return value.strip() + "\n";
  }

  public String removeKnownGutenbergDelimiters(String text) {
    var start = text.indexOf("*** START OF THE PROJECT GUTENBERG EBOOK");
    var end = text.indexOf("*** END OF THE PROJECT GUTENBERG EBOOK");
    if (start >= 0 && end > start) {
      var startLine = text.indexOf('\n', start);
      return text.substring(startLine < 0 ? start : startLine + 1, end);
    }
    return text;
  }
}
