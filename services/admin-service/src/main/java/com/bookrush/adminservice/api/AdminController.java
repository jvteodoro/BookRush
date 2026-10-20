package com.bookrush.adminservice.api;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.bookrush.adminservice.KeycloakAdminClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final JdbcTemplate jdbc;
  private final KeycloakAdminClient keycloak;

  public AdminController(JdbcTemplate jdbc, KeycloakAdminClient keycloak) {
    this.jdbc = jdbc;
    this.keycloak = keycloak;
  }

  private String actor(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  private void requireOperator(Authentication authentication) {
    if (authentication == null || authentication.getAuthorities().stream()
        .noneMatch(authority -> authority.getAuthority().equals("ROLE_platform-admins")
            || authority.getAuthority().equals("ROLE_operators")
            || authority.getAuthority().equals("ROLE_platform-admin")
            || authority.getAuthority().equals("ROLE_platform-operator")
            || authority.getAuthority().equals("ROLE_OPERATOR"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "operator permission required");
    }
  }

  @PostMapping("/audit")
  public Map<String, Object> audit(@RequestBody Map<String, Object> body, Principal principal,
      Authentication authentication) {
    requireOperator(authentication);
    UUID id = UUID.randomUUID();
    String action = String.valueOf(body.getOrDefault("action", "UNKNOWN"));
    String target = String.valueOf(body.getOrDefault("target", ""));
    jdbc.update("INSERT INTO admin.audit_log(id, actor_subject, action, target, details) "
        + "VALUES (?, ?, ?, ?, ?::jsonb)", id, actor(principal), action, target, "{}");
    return Map.of("id", id, "recorded", true);
  }

  @PostMapping("/moderation")
  public Object moderate(@RequestBody Map<String, Object> body, Principal principal,
      Authentication authentication) {
    requireOperator(authentication);
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO admin.moderation(id, target, decision, actor_subject) VALUES (?, ?, ?, ?)",
        id, String.valueOf(body.get("target")), String.valueOf(body.get("decision")), actor(principal));
    return Map.of("id", id, "status", "RECORDED");
  }

  @GetMapping("/moderation")
  public Object moderation(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT id, target, decision, actor_subject, created_at "
        + "FROM admin.moderation ORDER BY created_at DESC LIMIT 100");
  }

  @GetMapping("/reports")
  public Object reports(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT r.id, r.reporter, "
        + "COALESCE(NULLIF(reporter_profile.display_name, ''), r.reporter) AS reporter_name, "
        + "r.book_id, r.comment_id, r.reason, r.status, r.created_at, "
        + "r.reviewed_by_subject, r.reviewed_at, r.resolved_at, r.resolution_note, "
        + "c.body AS comment_body, "
        + "COALESCE(NULLIF(c.author_display_name, ''), NULLIF(author_profile.display_name, ''), c.subject_key) "
        + "AS comment_author_name "
        + "FROM social.reports r "
        + "LEFT JOIN social.comments c ON c.id=r.comment_id "
        + "LEFT JOIN reader_profile.profile reporter_profile ON reporter_profile.subject_key=r.reporter "
        + "LEFT JOIN reader_profile.profile author_profile ON author_profile.subject_key=c.subject_key "
        + "ORDER BY r.created_at DESC LIMIT 100");
  }

  public record ReportStatusChange(String status, String note) {}

  @PostMapping("/reports/{reportId}/status")
  @Transactional
  public Object updateReportStatus(@PathVariable UUID reportId,
      @RequestBody ReportStatusChange change, Principal principal, Authentication authentication) {
    requireOperator(authentication);
    if (change == null || !Set.of("OPEN", "IN_REVIEW", "RESOLVED", "DISMISSED").contains(change.status())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported report status");
    }
    String note = change.note() == null ? "" : change.note().trim();
    if ((Set.of("RESOLVED", "DISMISSED").contains(change.status()) && note.length() < 3)
        || note.length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "terminal report decisions require a note with 3 to 200 characters");
    }
    String operator = actor(principal);
    boolean terminal = Set.of("RESOLVED", "DISMISSED").contains(change.status());
    int updated = jdbc.update("UPDATE social.reports SET status=?, reviewed_by_subject=?, reviewed_at=now(), "
        + "resolved_at=CASE WHEN ? THEN now() ELSE NULL END, resolution_note=? WHERE id=?",
        change.status(), operator, terminal, note.isBlank() ? null : note, reportId);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "report not found");
    }
    jdbc.update("INSERT INTO admin.audit_log(id, actor_subject, action, target, details) "
        + "VALUES (?, ?, ?, ?, jsonb_build_object('status', ?, 'note', ?))",
        UUID.randomUUID(), operator, "REPORT_STATUS_CHANGED", reportId.toString(),
        change.status(), note);
    return Map.of("reportId", reportId, "status", change.status(), "updated", true);
  }

  @GetMapping("/moderation/comments")
  public Object comments(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT c.id, c.book_id, c.subject_key, c.body, c.created_at, "
        + "COALESCE(NULLIF(c.author_display_name, ''), NULLIF(p.display_name, ''), c.subject_key) AS author_name, "
        + "COALESCE(m.visibility, 'VISIBLE') AS visibility, m.reason_code, m.updated_by_subject, m.updated_at "
        + "FROM social.comments c LEFT JOIN social.comment_moderation_state m ON m.comment_id=c.id "
        + "LEFT JOIN reader_profile.profile p ON p.subject_key=c.subject_key "
        + "ORDER BY c.created_at DESC LIMIT 250");
  }

  @PostMapping("/moderation/comments")
  public Object moderateComment(@RequestBody Map<String, Object> body, Principal principal,
      Authentication authentication) {
    requireOperator(authentication);
    String commentId = String.valueOf(body.get("commentId"));
    String visibility = String.valueOf(body.getOrDefault("visibility", "VISIBLE"));
    if (!Set.of("VISIBLE", "HIDDEN", "REMOVED").contains(visibility)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported visibility");
    }
    UUID id;
    try { id = UUID.fromString(commentId); }
    catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid commentId"); }
    String operator = actor(principal);
    String reason = String.valueOf(body.getOrDefault("reasonCode", "ADMIN_DECISION"));
    int updated = jdbc.update("INSERT INTO social.comment_moderation_state "
        + "(comment_id, visibility, reason_code, updated_by_subject, updated_at) "
        + "SELECT id, ?, ?, ?, now() FROM social.comments WHERE id=? "
        + "ON CONFLICT (comment_id) DO UPDATE SET visibility=EXCLUDED.visibility, "
        + "reason_code=EXCLUDED.reason_code, updated_by_subject=EXCLUDED.updated_by_subject, "
        + "updated_at=EXCLUDED.updated_at",
        visibility, reason, operator, id);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "comment not found");
    return Map.of("commentId", id, "visibility", visibility, "updated", true);
  }

  @GetMapping("/users")
  public Object users(@RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page or size is outside the allowed range");
    }
    var users = keycloak.users(search, page, size).stream().map(user -> Map.of(
        "subjectKey", String.valueOf(user.get("id")),
        "username", String.valueOf(user.getOrDefault("username", "")),
        "email", String.valueOf(user.getOrDefault("email", "")),
        "firstName", String.valueOf(user.getOrDefault("firstName", "")),
        "lastName", String.valueOf(user.getOrDefault("lastName", "")),
        "enabled", !Boolean.FALSE.equals(user.get("enabled")),
        "createdTimestamp", user.getOrDefault("createdTimestamp", 0))).toList();
    long total = keycloak.count(search);
    return Map.of("items", users, "page", page, "size", size,
        "totalItems", total, "last", (long) (page + 1) * size >= total);
  }

  @PostMapping("/users/{subjectKey}/disable")
  public Object disable(@PathVariable String subjectKey, @RequestBody UserStatusChange change,
      Principal principal, Authentication authentication) {
    return setEnabled(subjectKey, false, change, principal, authentication);
  }

  @PostMapping("/users/{subjectKey}/enable")
  public Object enable(@PathVariable String subjectKey, @RequestBody UserStatusChange change,
      Principal principal, Authentication authentication) {
    return setEnabled(subjectKey, true, change, principal, authentication);
  }

  public record UserStatusChange(String reason) {}

  private Object setEnabled(String subjectKey, boolean enabled, UserStatusChange change,
      Principal principal, Authentication authentication) {
    requireOperator(authentication);
    String actor = actor(principal);
    if (subjectKey == null || subjectKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subjectKey is required");
    }
    String reason = change == null || change.reason() == null ? "" : change.reason().trim();
    if (reason.length() < 3 || reason.length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason must contain 3 to 200 characters");
    }
    if (subjectKey.equals(actor)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "operators cannot change their own account status");
    }
    keycloak.setEnabled(subjectKey, enabled);
    jdbc.update("INSERT INTO admin.audit_log(id, actor_subject, action, target, details) "
            + "VALUES (?, ?, ?, ?, jsonb_build_object('source', 'keycloak-admin-api', 'enabled', ?, 'reason', ?))",
        UUID.randomUUID(), actor, enabled ? "USER_ENABLED" : "USER_DISABLED", subjectKey,
        enabled, reason);
    jdbc.update("INSERT INTO admin.user_restriction(id, target_subject_key, status, reason_code, actor_subject, "
            + "keycloak_command_key, confirmed_at, details) VALUES (?, ?, ?, ?, ?, ?, now(), "
            + "jsonb_build_object('source', 'keycloak-admin-api', 'enabled', ?, 'reason', ?))",
        UUID.randomUUID(), subjectKey, enabled ? "REVOKED" : "ACTIVE", enabled ? "ADMIN_RESTORE" : "ADMIN_DISABLE",
        actor, "admin-" + UUID.randomUUID(), enabled, reason);
    return Map.of("subjectKey", subjectKey, "enabled", enabled, "updated", true);
  }

  @GetMapping("/kpis")
  public Object kpis(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return Map.of(
        "users", count("SELECT count(*) FROM reader_profile.profile"),
        "reports", count("SELECT count(*) FROM social.reports WHERE status IN ('OPEN', 'IN_REVIEW')"),
        "comments", count("SELECT count(*) FROM social.comments"),
        "moderationActions", count("SELECT count(*) FROM admin.moderation"),
        "auditEntries", count("SELECT count(*) FROM admin.audit_log"));
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  @GetMapping("/audit")
  public Object list(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT id, actor_subject, action, target, created_at "
        + "FROM admin.audit_log ORDER BY created_at DESC LIMIT 100");
  }
}
