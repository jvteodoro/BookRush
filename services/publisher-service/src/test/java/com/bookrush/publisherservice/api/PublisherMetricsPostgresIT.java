package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bookrush.publisherservice.PublisherMetricsClient;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

/** Run explicitly against a disposable PostgreSQL database using TEST_DATABASE_URL. */
class PublisherMetricsPostgresIT {
  @Test void onlyApprovedOwnedPublicationsGrantMetricsAccess() {
    var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),"bookrush_test","test-only-not-for-production");
    Flyway.configure().dataSource(source).schemas("publisher").load().migrate();
    var jdbc = new JdbcTemplate(source); var behavior = mock(PublisherMetricsClient.class);
    var controller = new PublisherMetricsController(jdbc,behavior);
    UUID draft=UUID.randomUUID(), published=UUID.randomUUID(), book=UUID.randomUUID(), work=UUID.randomUUID();
    try {
      jdbc.update("insert into publisher.submission(id,subject_key,title,catalog_book_id) values(?,?,?,?),(?,?,?,?)",draft,"owner","Untrusted catalog ID",book,published,"owner","Published",book);
      var unlinked=(Map<?,?>)controller.metrics(draft,()->"owner",30);
      assertEquals("NOT_LINKED",unlinked.get("status")); verifyNoInteractions(behavior);
      jdbc.update("insert into publisher.published_work(id,submission_id,book_id,publisher_subject_key) values(?,?,?,?)",work,published,book,"owner");
      when(behavior.metrics(book,30)).thenReturn(List.of());
      assertEquals("AVAILABLE",((Map<?,?>)controller.metrics(published,()->"owner",30)).get("status"));
      assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.metrics(published,()->"other-account",30)).getStatusCode().value());
      verify(behavior,times(1)).metrics(book,30);
      Flyway.configure().dataSource(source).schemas("publisher").load().validate();
    } finally {
      jdbc.update("delete from publisher.published_work where id=?",work);
      jdbc.update("delete from publisher.submission where id in (?,?)",draft,published);
    }
  }
}
