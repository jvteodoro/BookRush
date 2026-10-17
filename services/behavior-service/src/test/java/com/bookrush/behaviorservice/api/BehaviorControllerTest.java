package com.bookrush.behaviorservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookrush.behaviorservice.BehaviorMaintenance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BehaviorControllerTest {
  @Test
  void manualRebuildIncludesSocialAggregates() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    BehaviorController controller =
        new BehaviorController(jdbc, org.mockito.Mockito.mock(BehaviorMaintenance.class));

    controller.rebuild();

    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("shares = excluded.shares"));
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("event_type = 'BOOK_SHARE'"));
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("event_type = 'BOOK_COMMENT'"));
  }

  @Test
  void dashboardAggregatesClampTheRequestedWindow() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(365))).thenReturn(List.of(Map.of("day", "today")));
    BehaviorController controller =
        new BehaviorController(jdbc, org.mockito.Mockito.mock(BehaviorMaintenance.class));

    Map<String, Object> response = controller.dashboardAggregates(999);

    assertEquals(365, response.get("days"));
    assertEquals(1, ((List<?>) response.get("rows")).size());
    verify(jdbc).queryForList(
        org.mockito.ArgumentMatchers.contains("sum(shares) shares"), eq(365));
  }

  @Test
  void aggregateReadModelsExposeSocialMetrics() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    BehaviorController controller =
        new BehaviorController(jdbc, org.mockito.Mockito.mock(BehaviorMaintenance.class));

    controller.aggregates();
    controller.bookAggregates(java.util.UUID.randomUUID());

    verify(jdbc).queryForList(
        org.mockito.ArgumentMatchers.contains("shares, comments"));
    verify(jdbc).queryForList(
        org.mockito.ArgumentMatchers.contains("reads, shares, comments"),
        org.mockito.ArgumentMatchers.any(Object[].class));
  }
}
