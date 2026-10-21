package com.bookrush.socialservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import com.bookrush.socialservice.BehaviorEventPublisher;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {
  private final JdbcTemplate jdbc;
  private final RestClient profiles;
  private final BehaviorEventPublisher behavior;

  public SocialController(JdbcTemplate jdbc,
      BehaviorEventPublisher behavior,
      @org.springframework.beans.factory.annotation.Value("${PROFILE_URL:http://reader-profile-service:8095}") String profileUrl) {
    this.jdbc = jdbc;
    this.behavior = behavior;
    this.profiles = RestClient.builder().baseUrl(profileUrl).build();
  }

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  private String displayName(Principal principal) {
    if (principal instanceof JwtAuthenticationToken jwt) {
      Object preferred = jwt.getToken().getClaims().get("preferred_username");
      if (preferred instanceof String value && !value.isBlank() && value.length() <= 160) {
        return value;
      }
      Object name = jwt.getToken().getClaims().get("name");
      if (name instanceof String value && !value.isBlank() && value.length() <= 160) {
        return value;
      }
    }
    return subject(principal);
  }

  private String profileDisplayName(String identity, HttpServletRequest request) {
    String bearer = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
    try {
      var response = profiles.get().uri(uri -> uri.path("/api/v1/profile/public")
          .queryParam("subject", identity).build())
          .headers(headers -> { if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer); })
          .retrieve().body(Map.class);
      Object value = response == null ? null : response.get("displayName");
      return value instanceof String name && !name.isBlank() && name.length() <= 160 ? name : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void requireOperator(Authentication authentication) {
    if (authentication == null || authentication.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_platform-admins")
            || a.getAuthority().equals("ROLE_operators")
            || a.getAuthority().equals("ROLE_OPERATOR"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "operator permission required");
    }
  }

  private void activity(String actor, String type, UUID bookId, UUID commentId, String target) {
    jdbc.update("INSERT INTO social.activity(id, actor_subject_key, activity_type, book_id, comment_id, target_subject_key, occurred_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, now())", UUID.randomUUID(), actor, type, bookId, commentId, target);
  }

  private Map<String, Object> correlation(HttpServletRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : Map.of(
        "recommendationItemId", "X-BookRush-Recommendation-Item",
        "impressionId", "X-BookRush-Impression").entrySet()) {
      String value = request == null ? null : request.getHeader(entry.getValue());
      if (value == null || value.isBlank()) continue;
      try {
        payload.put(entry.getKey(), UUID.fromString(value));
      } catch (IllegalArgumentException ignored) {
        // Correlation is optional and must not block a social action.
      }
    }
    return payload;
  }

  @PutMapping("/books/{bookId}/like")
  public Map<String, Object> like(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    jdbc.update("INSERT INTO social.likes(subject_key, book_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        subject(principal), bookId);
    String actor = subject(principal);
    activity(actor, "LIKE", bookId, null, null);
    behavior.publish("BOOK_LIKE", bookId, correlation(request), request.getHeader(HttpHeaders.AUTHORIZATION));
    return Map.of("bookId", bookId, "liked", true);
  }

  @DeleteMapping("/books/{bookId}/like")
  public void unlike(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    jdbc.update("DELETE FROM social.likes WHERE subject_key = ? AND book_id = ?",
        subject(principal), bookId);
    behavior.publish("BOOK_UNLIKE", bookId, correlation(request), request.getHeader(HttpHeaders.AUTHORIZATION));
  }

  @PutMapping("/follow")
  public Map<String, Object> follow(@RequestParam String user, Principal principal, HttpServletRequest request) {
    String current = subject(principal);
    if (current.equals(user)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot follow self");
    }
    jdbc.update("INSERT INTO social.follows(follower, followed) VALUES (?, ?) ON CONFLICT DO NOTHING",
        current, user);
    activity(current, "FOLLOW", null, null, user);
    behavior.publish("FOLLOW", null, Map.of("followed", user), request.getHeader(HttpHeaders.AUTHORIZATION));
    return Map.of("user", user, "following", true);
  }

  @GetMapping("/follow")
  public Map<String, Object> followStatus(@RequestParam String user, Principal principal) {
    boolean following = jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM social.follows WHERE follower = ? AND followed = ?)",
        Boolean.class, subject(principal), user);
    return Map.of("user", user, "following", following);
  }

  @DeleteMapping("/follow")
  public void unfollow(@RequestParam String user, Principal principal, HttpServletRequest request) {
    jdbc.update("DELETE FROM social.follows WHERE follower = ? AND followed = ?",
        subject(principal), user);
    behavior.publish("UNFOLLOW", null, Map.of("followed", user), request.getHeader(HttpHeaders.AUTHORIZATION));
  }

  public record Report(UUID bookId, UUID commentId, @NotBlank @Size(max = 120) String reason) {}

  @PostMapping("/reports")
  public Object report(@Valid @RequestBody Report report, Principal principal, HttpServletRequest request) {
    if (report.bookId() == null && report.commentId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bookId or commentId is required");
    }
    if (report.commentId() != null) {
      Boolean exists = jdbc.queryForObject(
          "SELECT EXISTS (SELECT 1 FROM social.comments WHERE id = ?)", Boolean.class, report.commentId());
      if (!Boolean.TRUE.equals(exists)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "comment not found");
      }
    }
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO social.reports(id, reporter, book_id, comment_id, reason) "
        + "VALUES (?, ?, ?, ?, ?)", id, subject(principal), report.bookId(), report.commentId(),
        report.reason());
    behavior.publish(report.commentId() == null ? "BOOK_REPORT" : "COMMENT_REPORT", report.bookId(),
        Map.of("reason", report.reason()), request.getHeader(HttpHeaders.AUTHORIZATION));
    return Map.of("id", id, "status", "OPEN");
  }

  public record Share(@NotBlank @Size(max = 40) String channel) {}

  @PostMapping("/books/{bookId}/share")
  public Object share(@PathVariable UUID bookId, @Valid @RequestBody Share share, Principal principal, HttpServletRequest request) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO social.shares(id, subject_key, book_id, channel) VALUES (?, ?, ?, ?)",
        id, subject(principal), bookId, share.channel());
    activity(subject(principal), "SHARE", bookId, null, null);
    Map<String, Object> payload = new LinkedHashMap<>(correlation(request));
    payload.put("channel", share.channel());
    behavior.publish("BOOK_SHARE", bookId, payload, request.getHeader(HttpHeaders.AUTHORIZATION));
    return Map.of("id", id, "bookId", bookId, "channel", share.channel(), "recorded", true);
  }

  @GetMapping("/books/{bookId}/likes")
  public Map<String, Object> likes(@PathVariable UUID bookId, Principal principal) {
    boolean liked = principal != null && jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM social.likes WHERE book_id = ? AND subject_key = ?)",
        Boolean.class, bookId, principal.getName());
    return Map.of("bookId", bookId,
        "count", jdbc.queryForObject("SELECT count(*) FROM social.likes WHERE book_id = ?",
            Long.class, bookId), "liked", liked);
  }

  public record Comment(@NotBlank @Size(max = 2000) String body) {}

  @PostMapping("/books/{bookId}/comments")
  public Object comment(@PathVariable UUID bookId, @Valid @RequestBody Comment comment,
      Principal principal, HttpServletRequest request) {
    UUID id = UUID.randomUUID();
    String identity = subject(principal);
    String author = profileDisplayName(identity, request);
    jdbc.update("INSERT INTO social.comments(id, subject_key, author_display_name, book_id, body) "
        + "VALUES (?, ?, ?, ?, ?)", id, identity, author == null ? displayName(principal) : author, bookId,
        comment.body());
    jdbc.update("INSERT INTO social.comment_moderation_state(comment_id) VALUES (?) ON CONFLICT DO NOTHING", id);
    activity(identity, "COMMENT", bookId, id, null);
    behavior.publish("BOOK_COMMENT", bookId, Map.of(), request.getHeader(HttpHeaders.AUTHORIZATION));
    return Map.of("id", id, "bookId", bookId, "body", comment.body());
  }

  @GetMapping("/books/{bookId}/comments")
  public Object comments(@PathVariable UUID bookId, HttpServletRequest request) {
    var rows = jdbc.queryForList("SELECT c.id, c.subject_key, c.author_display_name, c.body, c.created_at "
        + "FROM social.comments c LEFT JOIN social.comment_moderation_state m ON m.comment_id=c.id "
        + "WHERE c.book_id = ? AND COALESCE(m.visibility, 'VISIBLE') = 'VISIBLE' ORDER BY c.created_at", bookId);
    rows.forEach(row -> {
      String identity = String.valueOf(row.get("subject_key"));
      String profileName = profileDisplayName(identity, request);
      Object stored = row.get("author_display_name");
      row.put("authorName", profileName != null ? profileName
          : stored instanceof String value && !value.isBlank() ? value : "Leitor");
      row.remove("author_display_name");
    });
    return rows;
  }

  public record ModerationDecision(@NotBlank @Size(max = 16) String visibility,
      @Size(max = 80) String reasonCode) {}

  @GetMapping("/moderation/reports")
  public Object moderationReports(Principal principal, Authentication authentication) {
    requireOperator(authentication);
    subject(principal);
    return jdbc.queryForList("SELECT r.id, r.reporter, "
        + "COALESCE(NULLIF(reporter_profile.display_name, ''), r.reporter) AS reporter_name, "
        + "r.book_id, r.comment_id, r.reason, r.status, r.created_at, "
        + "c.body AS comment_body, "
        + "COALESCE(NULLIF(c.author_display_name, ''), NULLIF(author_profile.display_name, ''), c.subject_key) "
        + "AS comment_author_name FROM social.reports r "
        + "LEFT JOIN social.comments c ON c.id=r.comment_id "
        + "LEFT JOIN reader_profile.profile reporter_profile ON reporter_profile.subject_key=r.reporter "
        + "LEFT JOIN reader_profile.profile author_profile ON author_profile.subject_key=c.subject_key "
        + "ORDER BY r.created_at DESC LIMIT 250");
  }

  @PutMapping("/moderation/comments/{commentId}")
  public Object moderateComment(@PathVariable UUID commentId, @Valid @RequestBody ModerationDecision decision,
      Principal principal, Authentication authentication) {
    requireOperator(authentication);
    String actor = subject(principal);
    if (!Set.of("VISIBLE", "HIDDEN", "REMOVED").contains(decision.visibility())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported visibility");
    }
    int updated = jdbc.update("UPDATE social.comment_moderation_state SET visibility=?, reason_code=?, updated_by_subject=?, updated_at=now() WHERE comment_id=?",
        decision.visibility(), decision.reasonCode(), actor, commentId);
    if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "comment not found");
    return Map.of("commentId", commentId, "visibility", decision.visibility(), "updatedBy", actor);
  }
}
