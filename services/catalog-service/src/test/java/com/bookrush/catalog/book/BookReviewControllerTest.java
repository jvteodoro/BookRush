package com.bookrush.catalog.book;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BookReviewControllerTest {
  @Test
  void approvalCreatesAuditedTransitionForAuthenticatedReviewer() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
    Principal reviewer = () -> "reviewer-123";

    new BookReviewController(jdbc).approve(UUID.randomUUID(), reviewer);

    verify(jdbc).update(eq("UPDATE catalog.book SET status='ACTIVE' WHERE id=? AND status='REVIEW_REQUIRED'"), any(Object[].class));
    verify(jdbc, times(2)).update(anyString(), any(Object[].class));
  }

  @Test
  void revocationCreatesAuditedTransitionAndCanBeRequestedByReviewer() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
    Principal reviewer = () -> "reviewer-123";

    new BookReviewController(jdbc).revokeApproval(UUID.randomUUID(), reviewer);

    verify(jdbc, times(2)).update(anyString(), any(Object[].class));
  }

  @Test
  void approvalNeverWritesNullActorWhenPrincipalHasNoName() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
    Principal reviewer = () -> null;

    new BookReviewController(jdbc).approve(UUID.randomUUID(), reviewer);

    var audit = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(jdbc, times(2)).update(anyString(), audit.capture());
    org.assertj.core.api.Assertions.assertThat(audit.getAllValues().get(1))
        .contains("unknown");
  }
}
