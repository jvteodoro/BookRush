package com.bookrush.adminservice.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class ReportingControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ReportingController controller = new ReportingController(jdbc);
  private final Principal principal = () -> "analyst-1";

  @Test
  void rejectsReadersAndUnboundedQueries() {
    var reader = new TestingAuthenticationToken(principal, "credentials", "ROLE_reader");
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> controller.readerDay(30, principal, reader)).getStatusCode().value());
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> controller.readerDay(367, principal, analyst)).getStatusCode().value());
  }

  @Test
  void reportsUntrustedPopulationInsteadOfPretendingProductData() {
    when(jdbc.queryForList(anyString(), eq(7))).thenReturn(List.of(Map.of(
        "day", java.sql.Date.valueOf("2026-11-01"),
        "population", "UNCLASSIFIED", "platform", "UNKNOWN",
        "refreshed_at", Timestamp.from(Instant.parse("2026-11-04T12:00:00Z")),
        "source_watermark", Timestamp.from(Instant.parse("2026-11-04T11:59:00Z")),
        "active_users", 2L)));
    var analyst = new TestingAuthenticationToken(principal, "credentials", "ROLE_analysts");

    var response = controller.readerDay(7, principal, analyst);

    assertEquals("UNTRUSTED", response.get("status"));
    assertEquals("UNCLASSIFIED", response.get("population"));
  }
}
