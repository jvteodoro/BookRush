package com.bookrush.behaviorservice;

import static org.junit.jupiter.api.Assertions.*;
import com.bookrush.behaviorservice.api.PublisherBookMetricsController;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PublisherMetricsPostgresIT {
  @Test void boundsDaysAndBooksWithoutExposingReaderData() {
    var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),"bookrush_test","test-only-not-for-production");
    Flyway.configure().dataSource(source).schemas("behavior").load().migrate();
    var jdbc = new JdbcTemplate(source);UUID book=UUID.randomUUID(),other=UUID.randomUUID();
    try {
      jdbc.update("insert into behavior.daily_book(day,book_id,opens) values(current_date,?,2),(current_date-6,?,3),(current_date-7,?,50),(current_date+1,?,100),(current_date,?,200)",book,book,book,book,other);
      var rows=(List<Map<String,Object>>)new PublisherBookMetricsController(jdbc).metrics(book,7);
      assertEquals(2,rows.size());assertEquals(5L,rows.stream().mapToLong(r->((Number)r.get("opens")).longValue()).sum());
      assertTrue(rows.stream().allMatch(row->row.keySet().equals(Set.of("day","impressions","opens","reads","likes","shares","comments"))));
      Flyway.configure().dataSource(source).schemas("behavior").load().validate();
    } finally { jdbc.update("delete from behavior.daily_book where book_id in (?,?)",book,other); }
  }
}
