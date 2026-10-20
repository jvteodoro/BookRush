package com.bookrush.socialservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookrush.socialservice.BehaviorEventPublisher;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class SocialControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final BehaviorEventPublisher behavior = mock(BehaviorEventPublisher.class);
  private final SocialController controller = new SocialController(jdbc, behavior, "http://profile");
  private final Principal principal = () -> "subject-1";

  @Test
  void cannotFollowSelf() {
    assertThrows(ResponseStatusException.class, () -> controller.follow("subject-1", principal, null));
  }

  @Test
  void moderationRequiresOperator() {
    var authentication = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.moderationReports(principal, authentication));
  }

  @Test
  void invalidModerationVisibilityIsRejected() {
    var authentication = new TestingAuthenticationToken(principal, "credentials", "ROLE_operators");
    var decision = new SocialController.ModerationDecision("UNKNOWN", "bad");
    assertThrows(ResponseStatusException.class, () -> controller.moderateComment(
        java.util.UUID.randomUUID(), decision, principal, authentication));
  }

  @Test
  void reportRequiresABookOrCommentTarget() {
    assertThrows(ResponseStatusException.class, () -> controller.report(
        new SocialController.Report(null, null, "spam"), principal, mock(HttpServletRequest.class)));
  }

  @Test
  void commentReportIsStoredAndReturnedOpen() {
    UUID bookId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    when(jdbc.queryForObject(contains("social.comments"), eq(Boolean.class), eq(commentId)))
        .thenReturn(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) controller.report(
        new SocialController.Report(bookId, commentId, "conteúdo ofensivo"), principal,
        mock(HttpServletRequest.class));

    assertEquals("OPEN", result.get("status"));
    verify(jdbc).update(contains("INSERT INTO social.reports"),
        org.mockito.ArgumentMatchers.any(), eq("subject-1"), eq(bookId), eq(commentId),
        eq("conteúdo ofensivo"));
  }
}
