package com.bookrush.readerprofileservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ProfileControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ProfileController controller = new ProfileController(jdbc);
  private final Principal reader = () -> "reader-1";

  @Test
  void readsPersistedPrivacy() {
    when(jdbc.queryForList(anyString(), eq("reader-1")))
        .thenReturn(List.of(Map.of(
            "profile_visibility", "FOLLOWERS",
            "activity_visibility", "PRIVATE",
            "library_visibility", "PRIVATE",
            "recent_visibility", "PUBLIC",
            "social_graph_visibility", "FOLLOWERS")));

    var privacy = controller.privacy(reader);

    assertEquals("FOLLOWERS", privacy.profileVisibility());
    assertEquals("PRIVATE", privacy.activityVisibility());
    assertEquals("PUBLIC", privacy.recentVisibility());
  }

  @Test
  void rejectsAnonymousAndInvalidVisibility() {
    assertThrows(ResponseStatusException.class, () -> controller.privacy(null));
    assertThrows(
        ResponseStatusException.class,
        () -> controller.updatePrivacy(
            new ProfileController.Privacy("EVERYONE", "PUBLIC", "PRIVATE", "PRIVATE", "PUBLIC"),
            reader));
  }
}
