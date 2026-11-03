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
  private final DailyPlantService dailyPlant = org.mockito.Mockito.mock(DailyPlantService.class);
  private final ReaderStateController controller = new ReaderStateController(jdbc, behavior, dailyPlant);
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
  void plantUsesTheAuthenticatedSubject() {
    when(dailyPlant.get("iss:subject-1")).thenReturn(Map.of("stage", 2, "sad", false));
    org.junit.jupiter.api.Assertions.assertEquals(2, controller.plant(user).get("stage"));
    verify(dailyPlant).get("iss:subject-1");
    assertThrows(ResponseStatusException.class, () -> controller.plant(null));
  }

  @Test
  void readingIntervalIsScopedToTheAuthenticatedReader() {
    var id = UUID.randomUUID();
    var end = java.time.Instant.now();
    var interval = new ReaderStateController.ReadingInterval(id, 15, end);
    when(dailyPlant.record("iss:subject-1", id, 15, end)).thenReturn(Map.of("stage", 0));
    controller.readingTime(interval, user);
    verify(dailyPlant).record("iss:subject-1", id, 15, end);
    assertThrows(ResponseStatusException.class, () -> controller.readingTime(interval, null));
  }
}
