package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PublisherIngestionLinkControllerTest {
  @Test
  void linksIdempotentlyAndRejectsConflictingBook() {
    var jdbc = mock(JdbcTemplate.class);
    var submission = UUID.randomUUID();
    var book = UUID.randomUUID();
    when(jdbc.queryForList(any(String.class), eq(submission)))
        .thenReturn(List.of(Map.of("catalog_book_id", book.toString())));
    var controller = new PublisherIngestionLinkController(jdbc, "callback-secret");
    var body = new PublisherIngestionLinkController.LinkRequest(book, UUID.randomUUID(), "GUTENBERG", "1342");

    var response = controller.link(submission, "callback-secret", body);
    assertEquals(book, response.get("catalogBookId"));
    verify(jdbc).update(any(String.class), eq(book), any(), eq("GUTENBERG"), eq("1342"), eq(submission));

    var other = new PublisherIngestionLinkController.LinkRequest(UUID.randomUUID(), null, "GUTENBERG", "1342");
    assertThrows(ResponseStatusException.class, () -> controller.link(submission, "callback-secret", other));
  }

  @Test
  void rejectsInvalidCallbackCredential() {
    var controller = new PublisherIngestionLinkController(mock(JdbcTemplate.class), "callback-secret");
    assertThrows(
        ResponseStatusException.class,
        () -> controller.link(UUID.randomUUID(), "wrong", new PublisherIngestionLinkController.LinkRequest(UUID.randomUUID(), null, null, null)));
  }
}
