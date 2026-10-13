package com.bookrush.readerprofileservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ProfileControllerTest {
  private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
  private final ProfileController controller = new ProfileController(jdbc);
  private final Principal user = () -> "iss:reader-1";

  @Test
  void newProfilesExposePrivacySafeDefaults() {
    when(jdbc.queryForList(contains("profile_privacy"), eq("iss:reader-1")))
        .thenReturn(List.of());

    @SuppressWarnings("unchecked")
    Map<String, Object> profile = (Map<String, Object>) controller.get(user);

    assertEquals("PUBLIC", profile.get("profileVisibility"));
    assertEquals("PUBLIC", profile.get("activityVisibility"));
    assertEquals("PRIVATE", profile.get("libraryVisibility"));
    assertEquals("PRIVATE", profile.get("recentVisibility"));
    assertEquals("PUBLIC", profile.get("socialGraphVisibility"));
  }

  @Test
  void updatePersistsEachVisibilityIndependently() {
    when(jdbc.queryForList(contains("profile_privacy"), eq("iss:reader-1")))
        .thenReturn(List.of(Map.of("subject", "iss:reader-1")));
    var update =
        new ProfileController.Update(
            null,
            null,
            null,
            "followers",
            "private",
            "public",
            "followers",
            "private");

    controller.update(update, user);

    verify(jdbc)
        .update(
            contains("insert into reader_profile.profile_privacy"),
            eq("iss:reader-1"),
            eq("FOLLOWERS"),
            eq("PRIVATE"),
            eq("PUBLIC"),
            eq("FOLLOWERS"),
            eq("PRIVATE"),
            eq("FOLLOWERS"),
            eq("PRIVATE"),
            eq("PUBLIC"),
            eq("FOLLOWERS"),
            eq("PRIVATE"));
  }

  @Test
  void legacyIsPublicStillUpdatesProfileVisibility() {
    when(jdbc.queryForList(contains("profile_privacy"), eq("iss:reader-1")))
        .thenReturn(List.of(Map.of("subject", "iss:reader-1")));

    controller.update(
        new ProfileController.Update(null, null, false, null, null, null, null, null), user);

    verify(jdbc)
        .update(
            contains("insert into reader_profile.profile_privacy"),
            eq("iss:reader-1"),
            eq("PRIVATE"),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq("PRIVATE"),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
  }

  @Test
  void invalidVisibilityIsRejectedBeforeWriting() {
    var update =
        new ProfileController.Update(null, null, null, "FRIENDS", null, null, null, null);

    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> controller.update(update, user));

    assertEquals(400, error.getStatusCode().value());
    verify(jdbc, never()).update(contains("insert"), org.mockito.ArgumentMatchers.<Object[]>any());
  }
}
