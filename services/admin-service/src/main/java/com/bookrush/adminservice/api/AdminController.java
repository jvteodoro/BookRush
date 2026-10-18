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
    return jdbc.queryForList("SELECT id, reporter, book_id, comment_id, reason, status, created_at "
        + "FROM social.reports ORDER BY created_at DESC LIMIT 100");
  }

  @GetMapping("/moderation/comments")
  public Object comments(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT c.id, c.book_id, c.subject_key, c.body, c.created_at, "
        + "COALESCE(m.visibility, 'VISIBLE') AS visibility, m.reason_code, m.updated_by_subject, m.updated_at "
        + "FROM social.comments c LEFT JOIN social.comment_moderation_state m ON m.comment_id=c.id "
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
    int updated = jdbc.update("UPDATE social.comment_moderation_state SET visibility=?, reason_code=?, updated_by_subject=?, updated_at=now() WHERE comment_id=?",
        visibility, String.valueOf(body.getOrDefault("reasonCode", "ADMIN_DECISION")), actor(principal), id);
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
        "reports", count("SELECT count(*) FROM social.reports WHERE status = 'OPEN'"),
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
