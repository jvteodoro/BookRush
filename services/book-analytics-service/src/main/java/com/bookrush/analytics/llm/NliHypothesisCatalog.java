package com.bookrush.analytics.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, versioned hypothesis set. Hypotheses are not translated at runtime. */
public record NliHypothesisCatalog(String code, String version, Map<String, List<Hypothesis>> sets) {
  public NliHypothesisCatalog {
    if (code == null || code.isBlank() || version == null || version.isBlank() || sets == null) {
      throw new IllegalArgumentException("invalid hypothesis catalog");
    }
    sets = Map.copyOf(sets);
  }

  public static NliHypothesisCatalog load(InputStream input) {
    try {
      var root = new ObjectMapper().readTree(input);
      var sets = new java.util.LinkedHashMap<String, List<Hypothesis>>();
      root.path("sets").fields().forEachRemaining(entry -> {
        var values = new java.util.ArrayList<Hypothesis>();
        entry.getValue().forEach(node -> values.add(new Hypothesis(
            node.path("kind").asText(), node.path("key").asText(), node.path("text").asText())));
        sets.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(values));
      });
      return new NliHypothesisCatalog(root.path("code").asText(), root.path("version").asText(), sets);
    } catch (IOException e) {
      throw new IllegalStateException("unable to load NLI hypothesis catalog", e);
    }
  }

  public List<Hypothesis> forLanguage(String language) {
    return sets.getOrDefault(language == null ? "en" : language.toLowerCase(Locale.ROOT), List.of());
  }

  public record Hypothesis(String kind, String key, String text) {
    public Hypothesis {
      if (kind == null || key == null || text == null || kind.isBlank() || key.isBlank() || text.isBlank()) {
        throw new IllegalArgumentException("invalid NLI hypothesis");
      }
    }
  }
}
