package com.bookrush.socialservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookrush.socialservice.BehaviorEventPublisher;
import java.security.Principal;
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
}
