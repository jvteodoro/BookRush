package com.bookrush.readerprofileservice.api;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
  private final JdbcTemplate jdbc;

  public ProfileController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  @GetMapping
  public Object get(Principal principal) {
    String subject = subject(principal);
    syncIdentity(principal, subject);
    var rows = jdbc.queryForList(
        "select subject_key,display_name,bio,is_public,updated_at from reader_profile.profile where subject_key=?",
        subject);
    return rows.isEmpty() ? Map.of("subject", subject, "displayName", "") : rows.getFirst();
  }

  private void syncIdentity(Principal principal, String subject) {
    if (!(principal instanceof JwtAuthenticationToken authentication)) return;
    String username = authentication.getToken().getClaimAsString("preferred_username");
    String name = authentication.getToken().getClaimAsString("name");
    if (name == null || name.isBlank()) name = username;
    jdbc.update(
        """
        insert into reader_profile.profile(subject_key,display_name,username) values(?,?,?)
        on conflict(subject_key) do update set username=coalesce(nullif(excluded.username,''),reader_profile.profile.username),
          display_name=case when nullif(btrim(reader_profile.profile.display_name),'') is null
            then excluded.display_name else reader_profile.profile.display_name end
        """,
        subject, name, username);
  }

  @GetMapping("/public")
  public Object publicProfile(@RequestParam String subject) {
    var rows = jdbc.queryForList(
        "select subject_key,display_name as \"displayName\",bio from reader_profile.profile where subject_key=? and is_public=true",
        subject);
    return rows.isEmpty() ? Map.of("subject", subject, "displayName", "") : rows.getFirst();
  }

  public record Update(String displayName, String bio, Boolean isPublic) {}

  @PutMapping
  public Object update(@RequestBody Update update, Principal principal) {
    String subject = subject(principal);
    jdbc.update(
        "insert into reader_profile.profile(subject_key,display_name,bio,is_public) values(?,?,?,?) on conflict(subject_key) do update set display_name=excluded.display_name,bio=excluded.bio,is_public=excluded.is_public,updated_at=now()",
        subject,
        update.displayName(),
        update.bio(),
        update.isPublic() == null || update.isPublic());
    return get(principal);
  }

  public record Privacy(
      String profileVisibility,
      String activityVisibility,
      String libraryVisibility,
      String recentVisibility,
      String socialGraphVisibility) {}

  @GetMapping("/privacy")
  public Privacy privacy(Principal principal) {
    String subject = subject(principal);
    ensureProfile(subject);
    var rows = jdbc.queryForList(
        "select profile_visibility,activity_visibility,library_visibility,recent_visibility,social_graph_visibility from reader_profile.profile_privacy where subject_key=?",
        subject);
    if (rows.isEmpty()) {
      return new Privacy("PUBLIC", "PUBLIC", "PRIVATE", "PRIVATE", "PUBLIC");
    }
    var row = rows.getFirst();
    return new Privacy(
        String.valueOf(row.get("profile_visibility")),
        String.valueOf(row.get("activity_visibility")),
        String.valueOf(row.get("library_visibility")),
        String.valueOf(row.get("recent_visibility")),
        String.valueOf(row.get("social_graph_visibility")));
  }

  @PutMapping("/privacy")
  public Privacy updatePrivacy(@RequestBody Privacy privacy, Principal principal) {
    String subject = subject(principal);
    validate(privacy);
    ensureProfile(subject);
    jdbc.update(
        "insert into reader_profile.profile_privacy(subject_key,profile_visibility,activity_visibility,library_visibility,recent_visibility,social_graph_visibility) values(?,?,?,?,?,?) on conflict(subject_key) do update set profile_visibility=excluded.profile_visibility,activity_visibility=excluded.activity_visibility,library_visibility=excluded.library_visibility,recent_visibility=excluded.recent_visibility,social_graph_visibility=excluded.social_graph_visibility,updated_at=now()",
        subject,
        privacy.profileVisibility(),
        privacy.activityVisibility(),
        privacy.libraryVisibility(),
        privacy.recentVisibility(),
        privacy.socialGraphVisibility());
    jdbc.update("update reader_profile.profile set is_public=? where subject_key=?", "PUBLIC".equals(privacy.profileVisibility()), subject);
    return privacy(subjectPrincipal(subject));
  }

  private void ensureProfile(String subject) {
    jdbc.update(
        "insert into reader_profile.profile(subject_key,is_public) values(?,true) on conflict(subject_key) do nothing",
        subject);
  }

  private void validate(Privacy privacy) {
    if (privacy == null
        || !visibility(privacy.profileVisibility())
        || !visibility(privacy.activityVisibility())
        || !visibility(privacy.libraryVisibility())
        || !visibility(privacy.recentVisibility())
        || !visibility(privacy.socialGraphVisibility())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid privacy visibility");
    }
  }

  private boolean visibility(String value) {
    return "PUBLIC".equals(value) || "FOLLOWERS".equals(value) || "PRIVATE".equals(value);
  }

  private Principal subjectPrincipal(String subject) {
    return () -> subject;
  }
}
