package com.bookrush.ingestion.processing;

public final class HtmlTextExtractor {
  public String extract(String html) {
    var safe = html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ");
    safe = safe.replaceAll("(?is)<br\\s*/?>", "\n").replaceAll("(?is)</(p|div|h[1-6]|li|tr)>", "\n");
    var text = safe.replaceAll("(?s)<[^>]+>", " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    text = text.replaceAll("[ \\t]+([.,!?;:])", "$1").replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");
    return new PlainTextNormalizer().normalize(text);
  }
}
