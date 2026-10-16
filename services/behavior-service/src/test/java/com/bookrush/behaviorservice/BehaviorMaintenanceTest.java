package com.bookrush.behaviorservice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BehaviorMaintenanceTest {
  @Test
  void disabledMaintenanceDoesNotMutateTheDatabase() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    new BehaviorMaintenance(jdbc, false, 30).run();
    verifyNoInteractions(jdbc);
  }

  @Test
  void enabledMaintenanceRebuildsAggregatesPublishesWatermarkAndRetainsRows() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

    new BehaviorMaintenance(jdbc, true, 30).run();

    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO behavior.daily_book"));
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO behavior.outcome_attribution"));
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM behavior.rejects"),
        (Object[]) org.mockito.ArgumentMatchers.any());
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE FROM behavior.outbox"),
        (Object[]) org.mockito.ArgumentMatchers.any());
  }
}
