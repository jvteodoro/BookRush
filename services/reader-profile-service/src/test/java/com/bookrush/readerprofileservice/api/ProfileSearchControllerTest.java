package com.bookrush.readerprofileservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ProfileSearchControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ProfileSearchController controller = new ProfileSearchController(jdbc);
  private final Principal reader = () -> "reader";

  @Test
  void emptyQueriesDoNotEnumerateUsers() {
    assertTrue(controller.search(" @ ", 0, 20, reader).items().isEmpty());
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsAnonymousAndInvalidParametersBeforeQuerying() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/v1/profile/search").param("q", "ana")).andExpect(status().isUnauthorized());
    for (String value : List.of("-1", "10001", "abc")) {
      mvc.perform(get("/api/v1/profile/search").principal(reader).param("page", value))
          .andExpect(status().isBadRequest());
    }
    assertThrows(ResponseStatusException.class, () -> controller.search("a".repeat(101), 0, 20, reader));
    assertThrows(ResponseStatusException.class, () -> controller.search("ana", 0, 0, reader));
    assertThrows(ResponseStatusException.class, () -> controller.search("ana", 0, 51, reader));
    verifyNoInteractions(jdbc);
  }

  @Test
  @SuppressWarnings("unchecked")
  void normalizesLiteralSearchAndPreservesPagination() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ana_%"), eq("ana_%"))).thenReturn(21L);
    when(jdbc.query(anyString(), any(RowMapper.class), eq("ana_%"), eq("ana_%"), eq(20), eq(20L)))
        .thenReturn(List.of(new ProfileSearchController.User("reader", "Ana", "ana_%")));
    var result = controller.search(" @ANA_% ", 1, 20, reader);
    assertEquals(21, result.totalItems());
    assertEquals(2, result.totalPages());
    assertEquals(1, result.page());
    assertEquals("reader", result.items().getFirst().subject());
  }
}
