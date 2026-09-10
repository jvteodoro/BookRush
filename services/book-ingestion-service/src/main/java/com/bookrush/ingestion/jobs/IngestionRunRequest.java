package com.bookrush.ingestion.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import io.swagger.v3.oas.annotations.media.Schema;

/** Parameters accepted by the administrative Gutenberg import command. */
public record IngestionRunRequest(
    @Schema(description = "Código da fonte de dados", example = "GUTENBERG", defaultValue = "GUTENBERG")
    String source,
    @Schema(description = "IDs externos específicos. Para Gutenberg, use apenas números; vazio significa descobrir itens conforme a fonte.", example = "[\"1342\", \"84\"]")
    List<String> externalIds,
    @Schema(description = "Filtro de idiomas em ISO 639-1 minúsculo.", example = "[\"en\"]")
    List<String> languages,
    @Schema(description = "Limite máximo de itens processados nesta execução (1–10000).", example = "10", minimum = "1", maximum = "10000")
    @Min(1) @Max(10000) Integer maxItems,
    @Schema(description = "Quando true, executa descoberta e validações sem persistir alterações canônicas ou baixar assets.", example = "true")
    boolean dryRun,
    @Schema(description = "Quando true, continua para descoberta/download e normalização de assets; quando false, processa somente metadata.", example = "false")
    boolean processAssets) {
  public IngestionRunRequest {
    source = source == null || source.isBlank() ? "GUTENBERG" : source.trim().toUpperCase(Locale.ROOT);
    externalIds = normalize(externalIds, false);
    languages = normalize(languages, true);
    maxItems = maxItems == null ? 100 : maxItems;
    if (maxItems < 1 || maxItems > 10000) throw new IllegalArgumentException("maxItems must be between 1 and 10000");
    if (!externalIds.isEmpty() && externalIds.size() > maxItems) throw new IllegalArgumentException("externalIds cannot contain more than maxItems entries");
    if (source.equals("GUTENBERG") && externalIds.stream().anyMatch(id -> !id.matches("[1-9][0-9]*"))) {
      throw new IllegalArgumentException("GUTENBERG externalIds must be numeric identifiers");
    }
  }
  private static List<String> normalize(List<String> values, boolean language) {
    if (values == null) return List.of();
    var result = new ArrayList<String>();
    for (var value : values) {
      if (value == null || value.isBlank()) continue;
      var normalized = value.trim();
      if (language) normalized = normalized.toLowerCase(Locale.ROOT);
      if (!result.contains(normalized)) result.add(normalized);
    }
    result.sort(String::compareTo);
    return List.copyOf(result);
  }
}
