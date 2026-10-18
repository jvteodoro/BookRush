package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.bookrush.publisherservice.BehaviorEventPublisher;
import com.bookrush.publisherservice.PublisherIngestionClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PublisherControllerTest {
  private final PublisherController controller = new PublisherController(
      mock(JdbcTemplate.class), mock(PublisherIngestionClient.class), mock(BehaviorEventPublisher.class));

  @Test
  void submissionDetailsRequireAuthenticatedPublisher() {
    assertThrows(ResponseStatusException.class, () -> controller.get(UUID.randomUUID(), null));
  }

  @Test
  void withdrawalRequiresAuthenticatedPublisher() {
    assertThrows(ResponseStatusException.class, () -> controller.withdraw(UUID.randomUUID(), null, null));
  }
}
