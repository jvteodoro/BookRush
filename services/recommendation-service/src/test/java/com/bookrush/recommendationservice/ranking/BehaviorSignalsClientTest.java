package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BehaviorSignalsClientTest {
  @Test
  void fetchesBoundedSignalsWithDedicatedS2sTokenAndCachesPerSubject() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://keycloak/token")).andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"access_token\":\"behavior-token\",\"expires_in\":300}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://behavior/api/internal/v1/recommendation/signals"))
        .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer behavior-token"))
        .andRespond(withSuccess("{\"items\":[{\"bookId\":\"00000000-0000-0000-0000-000000000001\",\"liked\":true}]}",
            MediaType.APPLICATION_JSON));
    var props = new BehaviorSignalsProperties("http://behavior", "http://keycloak/token", "client", "secret",
        "bookrush-behavior", "bookrush.behavior.read", Duration.ofMinutes(1), Duration.ofSeconds(1),
        Duration.ofSeconds(1), 50);
    var client = new BehaviorSignalsClient(builder, props, new SimpleMeterRegistry(), false);

    assertEquals(1, client.find("subject-a").size());
    assertEquals(1, client.find("subject-a").getFirst().liked() ? 1 : 0);
    server.verify();
  }
}
