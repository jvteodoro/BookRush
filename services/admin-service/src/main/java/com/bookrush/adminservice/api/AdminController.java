package com.bookrush.adminservice.api;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final JdbcTemplate jdbc;

  public AdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

  @GetMapping("/audit")
  public Object list(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    actor(principal);
    return jdbc.queryForList("SELECT id, actor_subject, action, target, created_at "
        + "FROM admin.audit_log ORDER BY created_at DESC LIMIT 100");
  }
}
