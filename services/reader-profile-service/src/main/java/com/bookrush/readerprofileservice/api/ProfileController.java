package com.bookrush.readerprofileservice.api;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
  private static final String PROFILE_QUERY =
      """
      select p.subject_key as "subject", p.display_name as "displayName", p.bio,
             (coalesce(pp.profile_visibility,
               case when p.is_public then 'PUBLIC' else 'PRIVATE' end) = 'PUBLIC') as "isPublic",
             coalesce(pp.profile_visibility,
               case when p.is_public then 'PUBLIC' else 'PRIVATE' end) as "profileVisibility",
             coalesce(pp.activity_visibility, 'PUBLIC') as "activityVisibility",
             coalesce(pp.library_visibility, 'PRIVATE') as "libraryVisibility",
             coalesce(pp.recent_visibility, 'PRIVATE') as "recentVisibility",
             coalesce(pp.social_graph_visibility, 'PUBLIC') as "socialGraphVisibility",
             p.updated_at as "updatedAt"
        from reader_profile.profile p
        left join reader_profile.profile_privacy pp on pp.subject_key = p.subject_key
       where p.subject_key = ?
      """;

  private final JdbcTemplate jdbc;

  public ProfileController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private String subject(Principal principal) {
    return principal == null ? "anonymous" : principal.getName();
  }

  @GetMapping
  public Object get(Principal principal) {
    String subject = subject(principal);
    var rows = jdbc.queryForList(PROFILE_QUERY, subject);
    return rows.isEmpty() ? emptyProfile(subject) : rows.getFirst();
  }

  @GetMapping("/public")
  public Object publicProfile(@RequestParam String subject) {
    var rows =
        jdbc.queryForList(
            """
            select p.subject_key as "subject", p.display_name as "displayName", p.bio
              from reader_profile.profile p
              left join reader_profile.profile_privacy pp on pp.subject_key = p.subject_key
             where p.subject_key = ?
               and coalesce(pp.profile_visibility,
                 case when p.is_public then 'PUBLIC' else 'PRIVATE' end) = 'PUBLIC'
            """,
            subject);
    return rows.isEmpty() ? Map.of("subject", subject, "displayName", "") : rows.getFirst();
  }

  public record Update(
      String displayName,
      String bio,
      Boolean isPublic,
      String profileVisibility,
      String activityVisibility,
      String libraryVisibility,
      String recentVisibility,
      String socialGraphVisibility) {}

  @PutMapping
  @Transactional
  public Object update(@RequestBody Update update, Principal principal) {
    String subject = subject(principal);
    String profileVisibility =
        visibility(
            update.profileVisibility(),
            update.isPublic() == null ? null : (update.isPublic() ? "PUBLIC" : "PRIVATE"));
    String activityVisibility = visibility(update.activityVisibility(), null);
    String libraryVisibility = visibility(update.libraryVisibility(), null);
    String recentVisibility = visibility(update.recentVisibility(), null);
    String socialGraphVisibility = visibility(update.socialGraphVisibility(), null);

    Boolean publicProfile =
        profileVisibility == null ? null : profileVisibility.equals("PUBLIC");
    jdbc.update(
        """
        insert into reader_profile.profile(subject_key, display_name, bio, is_public)
        values (?, ?, ?, coalesce(?::boolean, true))
        on conflict(subject_key) do update set
          display_name = coalesce(excluded.display_name, reader_profile.profile.display_name),
          bio = coalesce(excluded.bio, reader_profile.profile.bio),
          is_public = coalesce(?::boolean, reader_profile.profile.is_public),
          updated_at = now()
        """,
        subject,
        update.displayName(),
        update.bio(),
        publicProfile,
        publicProfile);

    jdbc.update(
        """
        insert into reader_profile.profile_privacy(
          subject_key, profile_visibility, activity_visibility, library_visibility,
          recent_visibility, social_graph_visibility)
        values (?, coalesce(?, 'PUBLIC'), coalesce(?, 'PUBLIC'), coalesce(?, 'PRIVATE'),
                coalesce(?, 'PRIVATE'), coalesce(?, 'PUBLIC'))
        on conflict(subject_key) do update set
          profile_visibility = coalesce(?, reader_profile.profile_privacy.profile_visibility),
          activity_visibility = coalesce(?, reader_profile.profile_privacy.activity_visibility),
          library_visibility = coalesce(?, reader_profile.profile_privacy.library_visibility),
          recent_visibility = coalesce(?, reader_profile.profile_privacy.recent_visibility),
          social_graph_visibility = coalesce(?, reader_profile.profile_privacy.social_graph_visibility),
          updated_at = now()
        """,
        subject,
        profileVisibility,
        activityVisibility,
        libraryVisibility,
        recentVisibility,
        socialGraphVisibility,
        profileVisibility,
        activityVisibility,
        libraryVisibility,
        recentVisibility,
        socialGraphVisibility);
    return get(principal);
  }

  private static String visibility(String value, String fallback) {
    String candidate = value == null ? fallback : value;
    if (candidate == null) return null;
    String normalized = candidate.trim().toUpperCase();
    if (!normalized.equals("PUBLIC")
        && !normalized.equals("FOLLOWERS")
        && !normalized.equals("PRIVATE")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "visibility must be PUBLIC, FOLLOWERS or PRIVATE");
    }
    return normalized;
  }

  private static Map<String, Object> emptyProfile(String subject) {
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("subject", subject);
    profile.put("displayName", "");
    profile.put("isPublic", true);
    profile.put("profileVisibility", "PUBLIC");
    profile.put("activityVisibility", "PUBLIC");
    profile.put("libraryVisibility", "PRIVATE");
    profile.put("recentVisibility", "PRIVATE");
    profile.put("socialGraphVisibility", "PUBLIC");
    return profile;
  }
}
