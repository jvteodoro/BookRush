package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ReaderStateControllerTest {
  private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
  private final ReaderStateController controller = new ReaderStateController(jdbc);
  private final Principal user = () -> "iss:subject-1";

  @Test
  void openedUsesValidTwoParameterUpsert() {
    UUID bookId = UUID.randomUUID();
    controller.opened(bookId, user);
    verify(jdbc).update(
        eq("insert into reader_state.recent(subject_key,book_id) values(?,?) on conflict(subject_key,book_id) do update set last_opened_at=now()"),
        eq("iss:subject-1"), eq(bookId));
  }

  @Test
  void stateCannotBeWrittenWithoutAnAuthenticatedPrincipal() {
    assertThrows(ResponseStatusException.class, () -> controller.recent(null));
  }
}
