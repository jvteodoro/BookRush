package com.bookrush.readerprofileservice.api;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/profile/search")
public class ProfileSearchController {
  private final JdbcTemplate jdbc;

  public ProfileSearchController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record User(String subject, String displayName, String username) {}
  public record Page(List<User> items, long totalItems, long totalPages, int page, int size) {}

  private static final String FILTER = """
      from reader_profile.profile p
      left join reader_profile.profile_privacy privacy on privacy.subject_key=p.subject_key
      where p.is_public=true and coalesce(privacy.profile_visibility,'PUBLIC')='PUBLIC'
        and (strpos(lower(coalesce(p.display_name,'')),?)>0
          or strpos(lower(coalesce(p.username,'')),?)>0)
      """;

  @GetMapping
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    String query = q.strip();
    if (query.length() > 100 || page < 0 || page > 10000 || size < 1 || size > 50) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid search parameters");
    }
    if (query.startsWith("@")) query = query.substring(1).strip();
    if (query.isBlank()) return new Page(List.of(), 0, 0, page, size);
    query = query.toLowerCase(Locale.ROOT);
    long count = jdbc.queryForObject("select count(*) " + FILTER, Long.class, query, query);
    var users = jdbc.query(
        "select p.subject_key,p.display_name,p.username " + FILTER
            + " order by lower(coalesce(nullif(p.display_name,''),p.username,'')),p.subject_key limit ? offset ?",
        (row, index) -> new User(row.getString("subject_key"), row.getString("display_name"), row.getString("username")),
        query, query, size, (long) page * size);
    return new Page(users, count, (count + size - 1) / size, page, size);
  }
}
