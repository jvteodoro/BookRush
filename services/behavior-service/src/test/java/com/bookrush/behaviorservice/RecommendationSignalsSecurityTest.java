package com.bookrush.behaviorservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.bookrush.behaviorservice.api.RecommendationSignalsController;

@WebMvcTest(RecommendationSignalsController.class)
@Import(BehaviorSecurityConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties = {
    "bookrush.security.enabled=true",
    "bookrush.security.issuer=https://product.example/realms/bookrush",
    "bookrush.security.jwk-set-uri=http://localhost:1/product-certs",
    "bookrush.security.audience=account",
    "bookrush.security.s2s-issuer=https://platform.example/realms/bookrush-platform",
    "bookrush.security.s2s-jwk-set-uri=http://localhost:1/platform-certs",
    "bookrush.security.s2s-audience=bookrush-behavior",
    "bookrush.security.s2s-scope=bookrush.behavior.read",
    "bookrush.cors.allowed-origins=https://bookrush.example"
})
class RecommendationSignalsSecurityTest {
  @Autowired MockMvc mockMvc;
  @MockBean JdbcTemplate jdbc;
  @MockBean JwtDecoder productDecoder;

  @Test
  void missingTokenIsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/internal/v1/recommendation/signals")
        .contentType("application/json").content("{\"subject\":\"opaque\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validTokenWithoutScopeIsForbidden() throws Exception {
    mockMvc.perform(post("/api/internal/v1/recommendation/signals")
        .with(jwt().jwt(token -> token.claim("aud", List.of("bookrush-behavior"))))
        .contentType("application/json").content("{\"subject\":\"opaque\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void validS2sScopeReachesTheEndpoint() throws Exception {
    when(jdbc.query(anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), any(Object[].class), eq(Long.class))).thenReturn(0L);
    mockMvc.perform(post("/api/internal/v1/recommendation/signals")
        .with(jwt().jwt(token -> token.claim("aud", List.of("bookrush-behavior"))
            .claim("scope", "bookrush.behavior.read")))
        .contentType("application/json").content("{\"subject\":\"opaque\"}"))
        .andExpect(status().isOk());
  }
}
