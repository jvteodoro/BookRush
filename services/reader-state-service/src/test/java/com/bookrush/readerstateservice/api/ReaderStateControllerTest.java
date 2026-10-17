package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.bookrush.readerstateservice.BehaviorEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ReaderStateControllerTest {
  private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
  private final BehaviorEventPublisher behavior = org.mockito.Mockito.mock(BehaviorEventPublisher.class);
  private final ReaderStateController controller = new ReaderStateController(jdbc, behavior);
  private final Principal user = () -> "iss:subject-1";

  @Test
  void openedUsesValidTwoParameterUpsert() {
    UUID bookId = UUID.randomUUID();
    controller.opened(bookId, user, null);
    verify(jdbc).update(
        eq("insert into reader_state.recent(subject_key,book_id) values(?,?) on conflict(subject_key,book_id) do update set last_opened_at=now()"),
        eq("iss:subject-1"), eq(bookId));
  }

  @Test
  void stateCannotBeWrittenWithoutAnAuthenticatedPrincipal() {
    assertThrows(ResponseStatusException.class, () -> controller.recent(null));
  }

  @Test
  void plantProjectionIsVersionedAndDerivedFromReadingDays() {
    when(jdbc.queryForList(anyString(), eq("iss:subject-1")))
        .thenReturn(List.of(Map.of("day", Date.valueOf(LocalDate.now()))));
    when(jdbc.queryForObject(anyString(), eq(Number.class), any(Object[].class))).thenReturn(125L);

    var plant = controller.plant(user);

    org.junit.jupiter.api.Assertions.assertEquals("reading-minutes-v1", plant.get("formulaVersion"));
    org.junit.jupiter.api.Assertions.assertEquals(2, plant.get("stage"));
    org.junit.jupiter.api.Assertions.assertEquals(1, plant.get("streakDays"));
    org.junit.jupiter.api.Assertions.assertEquals(125L, plant.get("totalReadingMinutes"));
    verify(jdbc).update(anyString(), eq("iss:subject-1"), eq("reading-minutes-v1"), eq(2), eq(1),
        eq(125L), any(LocalDate.class));
  }
}
