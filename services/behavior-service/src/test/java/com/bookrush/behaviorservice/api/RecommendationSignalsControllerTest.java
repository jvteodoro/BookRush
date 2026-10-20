package com.bookrush.behaviorservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;

class RecommendationSignalsControllerTest {
  @Test
  void rejectsUnboundedPaginationBeforeTouchingTheDatabase() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    var controller = new RecommendationSignalsController(jdbc);

    ResponseEntity<?> response = controller.signals(
        new RecommendationSignalsController.SignalsRequest("opaque-subject", List.of(), 101, 0));

    assertEquals(400, response.getStatusCode().value());
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsMoreThanOneHundredBookIds() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    var controller = new RecommendationSignalsController(jdbc);
    List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID).limit(101).toList();

    ResponseEntity<?> response = controller.signals(
        new RecommendationSignalsController.SignalsRequest("opaque-subject", ids, 10, 0));

    assertEquals(400, response.getStatusCode().value());
    verifyNoInteractions(jdbc);
  }
}
