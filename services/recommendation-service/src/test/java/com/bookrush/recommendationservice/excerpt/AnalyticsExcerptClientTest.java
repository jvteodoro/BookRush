package com.bookrush.recommendationservice.excerpt;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnalyticsExcerptClientTest {
  @Test
  void obtainsTechnicalTokenAndCachesLineagePreservingExcerpt() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID book = UUID.randomUUID();
    UUID version = UUID.randomUUID();
    String hash = "0123456789012345678901234567890123456789012345678901234567890123";
    server.expect(requestTo("http://keycloak/token"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"technical\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    String payload = "{\"items\":[{\"id\":\"" + UUID.randomUUID()
        + "\",\"source_asset_version_id\":\"" + version
        + "\",\"text\":\"A body excerpt.\",\"text_sha256\":\"" + hash
        + "\",\"start_codepoint\":4,\"end_codepoint\":18,\"generation_method\":\"SENTENCE_WINDOW\",\"generator_version\":\"v1\",\"body_eligible\":true}]}";
    server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/internal/v1/content-analytics/books/" + book)))
        .andExpect(header("Authorization", "Bearer technical"))
        .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
    var policy = new ExcerptPolicyProperties(true, 100, "", 1600, 1, true,
        Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
        Duration.ofSeconds(2), 2, "http://analytics", "http://keycloak/token", "client", "secret", "aud", "scope");
    var client = new AnalyticsExcerptClient(builder, policy, new SimpleMeterRegistry(), false);

    FeedExcerpt result = client.find(book);
    assertNotNull(result);
    assertEquals(version, result.sourceAssetVersionId());
    assertEquals(hash, result.textSha256());
    assertSame(result, client.find(book));
    server.verify();
  }

  @Test
  void outageDegradesToNull() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://keycloak/token")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    var policy = new ExcerptPolicyProperties(true, 100, "", 1600, 1, true,
        Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
        Duration.ofSeconds(2), 2, "http://analytics", "http://keycloak/token", "client", "secret", "aud", "scope");
    assertNull(new AnalyticsExcerptClient(builder, policy, new SimpleMeterRegistry(), false).find(UUID.randomUUID()));
    server.verify();
  }
}
