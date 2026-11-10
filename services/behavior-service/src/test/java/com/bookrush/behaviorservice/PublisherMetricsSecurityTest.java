package com.bookrush.behaviorservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import com.bookrush.behaviorservice.api.PublisherBookMetricsController;

@WebMvcTest(PublisherBookMetricsController.class)
@Import(BehaviorSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "bookrush.security.enabled=true", "bookrush.security.s2s-issuer=https://platform.example/realms/bookrush-platform",
    "bookrush.security.s2s-jwk-set-uri=http://localhost:1/certs", "bookrush.security.s2s-audience=bookrush-behavior",
    "bookrush.cors.allowed-origins=https://bookrush.example"
})
class PublisherMetricsSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean JdbcTemplate jdbc;
  @MockBean JwtDecoder productDecoder;
  private final String url = "/api/internal/v1/publisher/metrics/books/" + UUID.randomUUID();
  @Test void missingTokenIsUnauthorized() throws Exception { mvc.perform(get(url)).andExpect(status().isUnauthorized()); }
  @Test void unrelatedReadScopeIsForbidden() throws Exception {
    mvc.perform(get(url).with(jwt().jwt(token -> token.claim("scope","bookrush.behavior.read")))).andExpect(status().isForbidden());
  }
  @Test void dedicatedScopeReadsMetrics() throws Exception {
    mvc.perform(get(url).with(jwt().jwt(token -> token.claim("scope","bookrush.publisher.metrics.read")))).andExpect(status().isOk());
  }
  @Test void rejectsOutOfRangeDays() throws Exception {
    mvc.perform(get(url+"?days=366").with(jwt().jwt(token -> token.claim("scope","bookrush.publisher.metrics.read")))).andExpect(status().isBadRequest());
  }
}
