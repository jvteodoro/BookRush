package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookrush.publisherservice.BehaviorEventPublisher;
import com.bookrush.publisherservice.PublisherIngestionClient;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class PublisherSubmissionTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PublisherIngestionClient ingestion = mock(PublisherIngestionClient.class);
  private final BehaviorEventPublisher behavior = mock(BehaviorEventPublisher.class);
  private final PublisherController controller = new PublisherController(jdbc, ingestion, behavior);
  private final UUID id = UUID.randomUUID();

  private void fixture(String status) {
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("owner"))).thenReturn("ACTIVE");
    when(jdbc.queryForList(contains("SELECT title,status"), eq(id), eq("owner")))
        .thenReturn(List.of(Map.of("title", "My book", "status", status)));
    when(jdbc.queryForList(contains("FROM publisher.staged_upload"), eq(id), eq("owner")))
        .thenReturn(List.of(Map.of("object_key", "fixture.epub", "content_type", "application/epub+zip", "sha256", "a".repeat(64))));
    when(jdbc.update(contains("SET status='SUBMITTED'"), eq(id), eq("owner"))).thenReturn(1);
  }

  @Test void finalizedUploadStartsIngestionAndEntersReviewQueue() {
    fixture("UPLOADED");
    assertEquals("PROCESSING", ((Map<?, ?>) controller.submit(id, () -> "owner", new MockHttpServletRequest())).get("status"));
    verify(ingestion).process(id, "My book", "fixture.epub", "books-staging", "application/epub+zip", "a".repeat(64));
    verify(jdbc).update(contains("INSERT INTO publisher.publication_review"), any(UUID.class), eq(id), eq(id));
  }

  @Test void ingestionFailureRestoresUploadedStateSoTheOwnerCanRetry() {
    fixture("UPLOADED");
    doThrow(new IllegalStateException("unavailable")).when(ingestion).process(any(), any(), any(), any(), any(), any());
    assertEquals(503, assertThrows(ResponseStatusException.class,
        () -> controller.submit(id, () -> "owner", new MockHttpServletRequest())).getStatusCode().value());
    verify(jdbc).update(contains("SET status='UPLOADED'"), eq(id), eq("owner"));
    verify(jdbc, never()).update(contains("INSERT INTO publisher.publication_review"), any(), any(), any());
  }

  @Test void draftWithoutFinalizedUploadCannotEnterReview() {
    fixture("DRAFT");
    assertEquals(409, assertThrows(ResponseStatusException.class,
        () -> controller.submit(id, () -> "owner", new MockHttpServletRequest())).getStatusCode().value());
    verifyNoInteractions(ingestion);
  }

  @Test void repeatedSubmissionDoesNotRepeatIngestion() {
    fixture("PROCESSING");
    assertEquals("PROCESSING", ((Map<?, ?>) controller.submit(id, () -> "owner", new MockHttpServletRequest())).get("status"));
    verifyNoInteractions(ingestion);
  }

  @Test void foreignDraftIsNotVisible() {
    fixture("UPLOADED");
    when(jdbc.queryForList(contains("SELECT title,status"), eq(id), eq("owner"))).thenReturn(List.of());
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> controller.submit(id, () -> "owner", new MockHttpServletRequest())).getStatusCode().value());
    verifyNoInteractions(ingestion);
  }

  @Test void revokedMembershipCannotSubmit() {
    fixture("UPLOADED");
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("owner"))).thenReturn("REVOKED");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.submit(id, () -> "owner", new MockHttpServletRequest())).getStatusCode().value());
    verifyNoInteractions(ingestion);
  }
}
