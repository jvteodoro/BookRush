package com.bookrush.adminservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.bookrush.adminservice.KeycloakAdminClient;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class AdminControllerTest {
  private final AdminController controller = new AdminController(mock(JdbcTemplate.class), mock(KeycloakAdminClient.class));
  private final Principal principal = () -> "operator-1";

  @Test
  void rejectsReaderFromKpis() {
    var authentication = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.kpis(principal, authentication));
  }

  @Test
  void rejectsReaderFromUserLifecycle() {
    var authentication = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.disable("subject-2", principal, authentication));
  }
}
