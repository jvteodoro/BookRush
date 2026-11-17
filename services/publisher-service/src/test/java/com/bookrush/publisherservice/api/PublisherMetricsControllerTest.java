package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bookrush.publisherservice.PublisherMetricsClient;
import java.security.Principal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PublisherMetricsControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PublisherMetricsClient behavior = mock(PublisherMetricsClient.class);
  private final PublisherMetricsController controller = new PublisherMetricsController(jdbc, behavior);
  private final UUID id = UUID.randomUUID();
  private final Principal owner = () -> "publisher-owner";
  private static final String SQL = "select w.book_id as catalog_book_id from publisher.submission s left join publisher.published_work w on w.submission_id=s.id and w.publisher_subject_key=s.subject_key where s.id=? and s.subject_key=?";
  @Test void requiresAuthenticationAndDoesNotQueryOtherOwners() {
    assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.metrics(id,null,30)).getStatusCode().value());
    verifyNoInteractions(jdbc,behavior);
    when(jdbc.queryForList(SQL,id,"publisher-owner")).thenReturn(List.of());
    assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.metrics(id,owner,30)).getStatusCode().value());
    verifyNoInteractions(behavior);
  }
  @Test void unlinkedIsNotZeroActivityAndDoesNotCallBehavior() {
    Map<String,Object> row = new HashMap<>(); row.put("catalog_book_id",null);
    when(jdbc.queryForList(SQL,id,owner.getName())).thenReturn(List.of(row));
    assertEquals("NOT_LINKED",((Map<?,?>)controller.metrics(id,owner,30)).get("status"));
    verifyNoInteractions(behavior);
  }
  @Test void returnsOnlyTheOwnedBookAndRequestedWindow() {
    UUID book = UUID.randomUUID();
    when(jdbc.queryForList(SQL,id,owner.getName())).thenReturn(List.of(Map.of("catalog_book_id",book)));
    var data = List.<Map<String,Object>>of(Map.of("day","2026-11-10","opens",7));
    when(behavior.metrics(book,7)).thenReturn(data);
    var result = (Map<?,?>)controller.metrics(id,owner,7);
    assertEquals(data,result.get("items")); assertEquals(7,result.get("days"));
    verify(behavior).metrics(book,7);
  }
  @Test void rejectsInvalidWindows() {
    for (int days : new int[]{0,366}) assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.metrics(id,owner,days)).getStatusCode().value());
    verifyNoInteractions(jdbc,behavior);
  }
}
