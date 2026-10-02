package com.bookrush.socialservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {
  private final JdbcTemplate jdbc;
  private final RestClient profiles;

  public SocialController(JdbcTemplate jdbc,
      @org.springframework.beans.factory.annotation.Value("${PROFILE_URL:http://reader-profile-service:8095}") String profileUrl) {
    this.jdbc = jdbc;
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

  @PutMapping("/books/{bookId}/like")
  public Map<String, Object> like(@PathVariable UUID bookId, Principal principal) {
    jdbc.update("INSERT INTO social.likes(subject_key, book_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        subject(principal), bookId);
    return Map.of("bookId", bookId, "liked", true);
  }

  @DeleteMapping("/books/{bookId}/like")
  public void unlike(@PathVariable UUID bookId, Principal principal) {
    jdbc.update("DELETE FROM social.likes WHERE subject_key = ? AND book_id = ?",
        subject(principal), bookId);
  }

  @PutMapping("/users/{user}/follow")
  public Map<String, Object> follow(@PathVariable String user, Principal principal) {
    String current = subject(principal);
    if (current.equals(user)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot follow self");
    }
    jdbc.update("INSERT INTO social.follows(follower, followed) VALUES (?, ?) ON CONFLICT DO NOTHING",
        current, user);
    return Map.of("user", user, "following", true);
  }

  @DeleteMapping("/users/{user}/follow")
  public void unfollow(@PathVariable String user, Principal principal) {
    jdbc.update("DELETE FROM social.follows WHERE follower = ? AND followed = ?",
        subject(principal), user);
  }

  public record Report(UUID bookId, UUID commentId, @NotBlank @Size(max = 120) String reason) {}

  @PostMapping("/reports")
  public Object report(@Valid @RequestBody Report report, Principal principal) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO social.reports(id, reporter, book_id, comment_id, reason) "
        + "VALUES (?, ?, ?, ?, ?)", id, subject(principal), report.bookId(), report.commentId(),
        report.reason());
    return Map.of("id", id, "status", "OPEN");
  }

  public record Share(@NotBlank @Size(max = 40) String channel) {}

  @PostMapping("/books/{bookId}/share")
  public Object share(@PathVariable UUID bookId, @Valid @RequestBody Share share, Principal principal) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO social.shares(id, subject_key, book_id, channel) VALUES (?, ?, ?, ?)",
        id, subject(principal), bookId, share.channel());
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
    return Map.of("id", id, "bookId", bookId, "body", comment.body());
  }

  @GetMapping("/books/{bookId}/comments")
  public Object comments(@PathVariable UUID bookId, HttpServletRequest request) {
    var rows = jdbc.queryForList("SELECT id, subject_key, author_display_name, body, created_at "
        + "FROM social.comments WHERE book_id = ? ORDER BY created_at", bookId);
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
}
