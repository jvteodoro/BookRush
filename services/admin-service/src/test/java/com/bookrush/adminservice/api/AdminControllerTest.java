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
      new TestingAuthenticationToken(principal, "credentials", "ROLE_platform-admin");

  @Test
  void rejectsReaderFromUserManagement() {
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.users("", 0, 25, principal, reader));
    assertThrows(ResponseStatusException.class, () -> controller.disable(
        "subject-2", new AdminController.UserStatusChange("abuse"), principal, reader));
  }

  @Test
  void acceptsCanonicalPlatformOperatorRole() {
    var realmOperator = new TestingAuthenticationToken(
        principal, "credentials", "ROLE_platform-operator");
    when(keycloak.users("", 0, 25)).thenReturn(List.of());
    when(keycloak.count("")).thenReturn(0L);

    @SuppressWarnings("unchecked")
    Map<String, Object> page =
        (Map<String, Object>) controller.users("", 0, 25, principal, realmOperator);

    assertEquals(0L, page.get("totalItems"));
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
  void feedLabReadsRecommendationLedgerOnlyForOperators() {
    var rows = List.of(Map.<String, Object>of(
        "book_id", "book-1", "book_title", "Book One", "model_version", "personalized-v1"));
    when(jdbc.queryForList(contains("FROM recommendation.item"), eq(50))).thenReturn(rows);

    var response = controller.feed(50, principal, operator);

    assertEquals(rows, response.get("items"));
    assertEquals(true, response.get("readOnly"));
    assertEquals("recommendation-ledger-v1", response.get("source"));
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertThrows(ResponseStatusException.class, () -> controller.feed(50, principal, reader));
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

  @Test
  void moderationCreatesMissingLegacyStateOrUpdatesExistingState() {
    var commentId = java.util.UUID.randomUUID();
    when(jdbc.queryForList(contains("FOR UPDATE"), eq(commentId)))
        .thenReturn(List.of(Map.of("id", commentId)));
    when(jdbc.queryForObject(contains("SELECT COALESCE"), eq(String.class), eq(commentId)))
        .thenReturn("VISIBLE");

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) controller.moderateComment(
        new AdminController.CommentModerationChange(commentId, "HIDDEN", "ADMIN_REVIEW"),
        principal, operator);

    assertEquals("HIDDEN", result.get("visibility"));
    assertEquals(true, result.get("updated"));
    verify(jdbc).update(contains("ON CONFLICT (comment_id) DO UPDATE"),
        eq(commentId), eq("HIDDEN"), eq("ADMIN_REVIEW"), eq("operator-1"));
    verify(jdbc).update(contains("INSERT INTO admin.audit_log"),
        org.mockito.ArgumentMatchers.any(), eq("operator-1"), eq(commentId.toString()),
        eq("VISIBLE"), eq("HIDDEN"), eq("ADMIN_REVIEW"));
  }

  @Test
  void resolvesReportWithRequiredNoteAndAuditTrail() {
    var reportId = java.util.UUID.randomUUID();
    when(jdbc.update(contains("UPDATE social.reports"), eq("RESOLVED"), eq("operator-1"),
        eq(true), eq("comment removed"), eq(reportId))).thenReturn(1);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) controller.updateReportStatus(reportId,
        new AdminController.ReportStatusChange("RESOLVED", "comment removed"), principal, operator);

    assertEquals("RESOLVED", result.get("status"));
    verify(jdbc).update(contains("INSERT INTO admin.audit_log"),
        org.mockito.ArgumentMatchers.any(), eq("operator-1"), eq("REPORT_STATUS_CHANGED"),
        eq(reportId.toString()), eq("RESOLVED"), eq("comment removed"));
  }

  @Test
  void rejectsTerminalReportDecisionWithoutExplanation() {
    var reportId = java.util.UUID.randomUUID();
    assertThrows(ResponseStatusException.class, () -> controller.updateReportStatus(reportId,
        new AdminController.ReportStatusChange("DISMISSED", ""), principal, operator));
    verify(jdbc, never()).update(contains("UPDATE social.reports"),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }
  @Test
  void commentModerationRejectsReadersAndInvalidDecisionsBeforeWriting() {
    var id = java.util.UUID.randomUUID();
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    var valid = new AdminController.CommentModerationChange(id, "HIDDEN", "spam review");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.moderateComment(valid, principal, reader)).getStatusCode().value());
    for (var change : List.of(
        new AdminController.CommentModerationChange(id, "UNKNOWN", "spam"),
        new AdminController.CommentModerationChange(null, "HIDDEN", "spam"),
        new AdminController.CommentModerationChange(id, "HIDDEN", " "),
        new AdminController.CommentModerationChange(id, "HIDDEN", "x".repeat(81)),
        new AdminController.CommentModerationChange(id, null, "spam"))) {
      assertEquals(400, assertThrows(ResponseStatusException.class,
          () -> controller.moderateComment(change, principal, operator)).getStatusCode().value());
    }
    org.mockito.Mockito.verifyNoInteractions(jdbc);
  }

  @Test
  void missingCommentIs404AndRepeatedDecisionDoesNotCreateDuplicateAudit() {
    var id = java.util.UUID.randomUUID();
    var change = new AdminController.CommentModerationChange(id, "HIDDEN", "spam review");
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> controller.moderateComment(change, principal, operator)).getStatusCode().value());
    when(jdbc.queryForList(contains("FOR UPDATE"), eq(id))).thenReturn(List.of(Map.of("id", id)));
    when(jdbc.queryForObject(contains("SELECT COALESCE"), eq(String.class), eq(id))).thenReturn("HIDDEN");
    @SuppressWarnings("unchecked")
    var response = (Map<String, Object>) controller.moderateComment(change, principal, operator);
    assertEquals(false, response.get("updated"));
    verify(jdbc, never()).update(contains("INSERT INTO social.comment_moderation_state"),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void commentPaginationValidatesFiltersAndPreservesLiteralSearch() {
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.commentPage("", "UNKNOWN", 0, 25, principal, operator)).getStatusCode().value());
    assertThrows(ResponseStatusException.class,
        () -> controller.commentPage("", "", -1, 25, principal, operator));
    assertThrows(ResponseStatusException.class,
        () -> controller.commentPage("x".repeat(201), "", 0, 25, principal, operator));
    when(jdbc.queryForObject(contains("SELECT count(*)"), eq(Long.class),
        eq("HIDDEN"), eq("HIDDEN"), eq("100%"), eq("100%"))).thenReturn(26L);
    @SuppressWarnings("unchecked")
    var response = (Map<String, Object>) controller.commentPage(" 100% ", "HIDDEN", 1, 25, principal, operator);
    assertEquals(26L, response.get("totalItems"));
    assertEquals(true, response.get("last"));
    verify(jdbc).queryForList(contains("strpos"), eq("HIDDEN"), eq("HIDDEN"), eq("100%"), eq("100%"), eq(25), eq(25L));
  }

}
