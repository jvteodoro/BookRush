package com.bookrush.behaviorservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BehaviorSecurityConfigurationTest {
  @Test
  void corsConfigurationUsesOnlyConfiguredOrigins() {
    var source = new BehaviorSecurityConfiguration().cors(
        "https://bookrush.jteodoro.tec.br, http://localhost:18080");

    var configuration = source.getCorsConfiguration(
        new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/v1/behavior/events"));

    assertEquals(
        java.util.List.of("https://bookrush.jteodoro.tec.br", "http://localhost:18080"),
        configuration.getAllowedOrigins());
    assertEquals(true, configuration.getAllowedMethods().contains("OPTIONS"));
    assertEquals(true, configuration.getAllowedHeaders().contains("Authorization"));
  }
}
