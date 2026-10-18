package com.bookrush.adminservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookrush.adminservice.KeycloakAdminClient;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class AdminControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
  private final AdminController controller = new AdminController(jdbc, keycloak);
  private final Principal principal = () -> "operator-1";
  private final TestingAuthenticationToken operator =
      new TestingAuthenticationToken(principal, "credentials", "ROLE_platform-admins");

  @Test
  void rejectsReaderFromUserManagement() {
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.users("", 0, 25, principal, reader));
    assertThrows(ResponseStatusException.class, () -> controller.disable(
        "subject-2", new AdminController.UserStatusChange("abuse"), principal, reader));
  }

  @Test
  void listsProductRealmUsersWithPagination() {
    when(keycloak.users("ana", 1, 20)).thenReturn(List.of(Map.of(
        "id", "subject-2", "username", "ana", "email", "ana@example.test", "enabled", true)));
    when(keycloak.count("ana")).thenReturn(21L);

    @SuppressWarnings("unchecked")
    Map<String, Object> page = (Map<String, Object>) controller.users("ana", 1, 20, principal, operator);

    assertEquals(21L, page.get("totalItems"));
    assertEquals(true, page.get("last"));
    assertEquals(1, ((List<?>) page.get("items")).size());
  }

  @Test
  void validatesPaginationAndLifecycleReasonBeforeCallingKeycloak() {
    assertThrows(ResponseStatusException.class, () -> controller.users("", -1, 25, principal, operator));
    assertThrows(ResponseStatusException.class, () -> controller.disable(
        "subject-2", new AdminController.UserStatusChange("x"), principal, operator));
    assertThrows(ResponseStatusException.class, () -> controller.disable(
        "operator-1", new AdminController.UserStatusChange("self change"), principal, operator));
    verify(keycloak, never()).setEnabled(eq("subject-2"), eq(false));
  }

  @Test
  void disablesUserOnlyAfterKeycloakAndRecordsReason() {
    controller.disable("subject-2", new AdminController.UserStatusChange("policy violation"),
        principal, operator);

    verify(keycloak).setEnabled("subject-2", false);
    verify(jdbc).update(contains("INSERT INTO admin.audit_log"),
        org.mockito.ArgumentMatchers.any(), eq("operator-1"), eq("USER_DISABLED"),
        eq("subject-2"), eq(false), eq("policy violation"));
    verify(jdbc).update(contains("INSERT INTO admin.user_restriction"),
        org.mockito.ArgumentMatchers.any(), eq("subject-2"), eq("ACTIVE"),
        eq("ADMIN_DISABLE"), eq("operator-1"), org.mockito.ArgumentMatchers.any(),
        eq(false), eq("policy violation"));
  }
}
