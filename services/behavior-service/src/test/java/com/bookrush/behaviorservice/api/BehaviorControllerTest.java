package com.bookrush.behaviorservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.bookrush.behaviorservice.BehaviorMaintenance;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;
import java.security.Principal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BehaviorControllerTest {
  @Test
  void manualRebuildIncludesSocialAggregates() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    BehaviorMaintenance maintenance = org.mockito.Mockito.mock(BehaviorMaintenance.class);
    BehaviorController controller = new BehaviorController(jdbc, maintenance);

    controller.rebuild();

    verify(maintenance).rebuildNow();
  }

  @Test
  void dashboardAggregatesClampTheRequestedWindow() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    org.mockito.Mockito.when(jdbc.queryForList(anyString(), eq(365))).thenReturn(List.of(Map.of("day", "today")));
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

  @Test
  void rejectsMalformedRecommendationCorrelation() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    BehaviorController controller = new BehaviorController(jdbc,
        org.mockito.Mockito.mock(BehaviorMaintenance.class));
    var payload = new ObjectMapper().readTree("{\"recommendationItemId\":\"not-a-uuid\"}");
    var event = new BehaviorController.Event("event-1", "BOOK_OPEN", null, null,
        UUID.randomUUID(), Instant.now(), payload);

    var response = controller.ingest(new BehaviorController.Batch(List.of(event)),
        (Principal) () -> "user-a");

    assertEquals(202, response.getStatusCode().value());
    verify(jdbc).update(eq("insert into behavior.rejects(id,event_key,reason,payload) values(?,?,?,?::jsonb)"),
        org.mockito.ArgumentMatchers.any(), eq("event-1"), eq("INVALID_CORRELATION"),
        org.mockito.ArgumentMatchers.anyString());
  }
}
