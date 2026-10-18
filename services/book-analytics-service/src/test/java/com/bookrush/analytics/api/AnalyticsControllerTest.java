package com.bookrush.analytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bookrush.analytics.observability.AnalyticsMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsControllerTest {
  @Test
  void rejectsAnInvalidJobListLimit() {
    var controller = new AnalyticsController(mock(JdbcTemplate.class), new ObjectMapper(), mock(AnalyticsMetrics.class));

    var response = controller.jobs(0);

    assertEquals(400, response.getStatusCode().value());
    assertTrue(response.getBody() instanceof Map<?, ?>);
    assertEquals("INVALID_LIMIT", response.getBody().get("code"));
  }
}
