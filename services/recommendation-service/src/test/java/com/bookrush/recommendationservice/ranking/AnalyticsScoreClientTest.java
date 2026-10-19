package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnalyticsScoreClientTest {
  @Test
  void requestsCandidatesInOneS2sBatchAndCachesTheResult() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID book = UUID.randomUUID();
    server.expect(requestTo("http://keycloak/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"technical\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://analytics/api/internal/v1/content-analytics/book-scores"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer technical"))
        .andExpect(content().json("{\"bookIds\":[\"" + book + "\"]}"))
        .andRespond(withSuccess("{\"items\":[{\"bookId\":\"" + book
            + "\",\"score\":0.87,\"rankerVersion\":\"EXCERPT_HEURISTIC_RANKER_V1\"}]}", MediaType.APPLICATION_JSON));
    var properties = new AnalyticsRankingProperties(true, "analytics-aware-v1", "http://analytics",
        "http://keycloak/token", "client", "secret", "aud", "scope", Duration.ofMinutes(1),
        Duration.ofSeconds(1), Duration.ofSeconds(1), 100);
    var client = new AnalyticsScoreClient(builder, properties, new SimpleMeterRegistry(), false);

    var first = client.find(List.of(book));
    var second = client.find(List.of(book));

    assertEquals(0.87, first.get(book).score());
    assertEquals(first, second);
    server.verify();
  }

  @Test
  void acceptsPartialScoreResponsesWithoutPoisoningTheCache() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID scored = UUID.randomUUID();
    UUID unscored = UUID.randomUUID();
    server.expect(requestTo("http://keycloak/token"))
        .andRespond(withSuccess("{\"access_token\":\"technical\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://analytics/api/internal/v1/content-analytics/book-scores"))
        .andRespond(withSuccess("{\"items\":[{\"bookId\":\"" + scored
            + "\",\"score\":0.87}]}", MediaType.APPLICATION_JSON));
    var properties = new AnalyticsRankingProperties(true, "analytics-aware-v1", "http://analytics",
        "http://keycloak/token", "client", "secret", "aud", "scope", Duration.ofMinutes(1),
        Duration.ofSeconds(1), Duration.ofSeconds(1), 100);
    var client = new AnalyticsScoreClient(builder, properties, new SimpleMeterRegistry(), false);

    var result = client.find(List.of(scored, unscored));

    assertEquals(0.87, result.get(scored).score());
    assertEquals(1, result.size());
    server.verify();
  }

  @Test
  void recordsTimeoutAndDurationWhenAnalyticsIsUnavailable() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    var registry = new SimpleMeterRegistry();
    UUID book = UUID.randomUUID();
    server.expect(requestTo("http://keycloak/token"))
        .andRespond(withSuccess("{\"access_token\":\"technical\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://analytics/api/internal/v1/content-analytics/book-scores"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withException(new IOException("analytics timeout")));
    var properties = new AnalyticsRankingProperties(true, "analytics-aware-v1", "http://analytics",
        "http://keycloak/token", "client", "secret", "aud", "scope", Duration.ofMinutes(1),
        Duration.ofSeconds(1), Duration.ofSeconds(1), 100);
    var client = new AnalyticsScoreClient(builder, properties, registry, false);

    assertTrue(client.find(List.of(book)).isEmpty());
    assertEquals(1, registry.counter("bookrush.feed.analytics_scores", "outcome", "timeout").count());
    assertEquals(1, registry.timer("bookrush.feed.analytics_scores.duration").count());
    server.verify();
  }
}
