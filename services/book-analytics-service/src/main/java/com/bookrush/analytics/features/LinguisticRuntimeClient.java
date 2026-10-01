package com.bookrush.analytics.features;

import com.bookrush.analytics.config.AnalyticsRuntimeProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Bounded internal client; unavailable runtime becomes a status, never a fabricated zero. */
public final class LinguisticRuntimeClient {
  public record Result(
      String status, String language, Map<String, Double> values, String warning) {}

  public record LanguageResult(String status, String language, double confidence, String warning) {}

  private final RestClient client;

  public LinguisticRuntimeClient(AnalyticsRuntimeProperties p) {
    var factory = new SimpleClientHttpRequestFactory();
    int timeout = Math.max(1, p.timeoutSeconds()) * 1000;
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    client = RestClient.builder().baseUrl(p.url()).requestFactory(factory).build();
  }

  @SuppressWarnings("unchecked")
  public LanguageResult language(String text) {
    try {
      var body =
          client
              .post()
              .uri("/v1/language")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("text", text))
              .retrieve()
              .body(Map.class);
      return new LanguageResult(
          String.valueOf(body.get("status")),
          String.valueOf(body.getOrDefault("language", "und")),
          number(body.get("confidence")),
          (String) body.get("warning"));
    } catch (Exception e) {
      return new LanguageResult("MODEL_UNAVAILABLE", "und", 0d, "runtime unavailable");
    }
  }

  @SuppressWarnings("unchecked")
  public Result analyze(String language, String text) {
    try {
      var body =
          client
              .post()
              .uri("/v1/linguistic")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("language", language, "text", text))
              .retrieve()
              .body(Map.class);
      return new Result(
          String.valueOf(body.get("status")),
          String.valueOf(body.get("language")),
          (Map<String, Double>) body.getOrDefault("values", Map.of()),
          (String) body.get("warning"));
    } catch (Exception e) {
      return new Result("MODEL_UNAVAILABLE", language, Map.of(), "runtime unavailable");
    }
  }

  public record EmbeddingResult(
      String status,
      String model,
      int dimension,
      java.util.List<java.util.List<Double>> vectors,
      java.util.List<String> inputHashes,
      String warning) {}

  @SuppressWarnings("unchecked")
  public EmbeddingResult embeddings(java.util.List<String> texts, int maxTokens) {
    try {
      var body =
          client
              .post()
              .uri("/v1/embeddings")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("texts", texts, "max_tokens", maxTokens))
              .retrieve()
              .body(Map.class);
      return new EmbeddingResult(
          String.valueOf(body.get("status")),
          String.valueOf(body.get("model")),
          ((Number) body.getOrDefault("dimension", 0)).intValue(),
          (java.util.List<java.util.List<Double>>)
              body.getOrDefault("vectors", java.util.List.of()),
          (java.util.List<String>) body.getOrDefault("input_hashes", java.util.List.of()),
          (String) body.get("warning"));
    } catch (Exception e) {
      return new EmbeddingResult(
          "MODEL_UNAVAILABLE",
          "bge-m3-dense-v1",
          0,
          java.util.List.of(),
          java.util.List.of(),
          "runtime unavailable");
    }
  }

  public record NliResult(
      String status, double entailment, double neutral, double contradiction, String warning) {
    public NliScore score() {
      return new NliScore(entailment, neutral, contradiction);
    }
  }

  @SuppressWarnings("unchecked")
  public NliResult nli(String text, String hypothesis) {
    try {
      var body =
          client
              .post()
              .uri("/v1/nli")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("text", text, "hypothesis", hypothesis))
              .retrieve()
              .body(Map.class);
      return new NliResult(
          String.valueOf(body.get("status")),
          number(body.get("entailment")),
          number(body.get("neutral")),
          number(body.get("contradiction")),
          (String) body.get("warning"));
    } catch (Exception e) {
      return new NliResult("MODEL_UNAVAILABLE", 0, 0, 0, "runtime unavailable");
    }
  }

  private static double number(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0d;
  }
}
