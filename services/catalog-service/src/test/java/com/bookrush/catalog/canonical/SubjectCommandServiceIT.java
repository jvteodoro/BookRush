package com.bookrush.catalog.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookrush.catalog.subject.SubjectCommandService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SubjectCommandServiceIT {
  @Autowired SubjectCommandService service;
  @Autowired JdbcTemplate jdbc;

  @Test
  void assignmentIsIdempotentAndSchemesRemainDistinct() {
    var book = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book(id,canonical_title) VALUES (?,?)", book, "Subject fixture");
    var command = new SubjectCommandService.SubjectCommand(book, "open_library", "  Gothic  Fiction ", "GUTENBERG", null, null, "SOURCE");
    assertEquals("CREATED", service.assign(command).get("result"));
    assertEquals("NOOP", service.assign(command).get("result"));
    var other = new SubjectCommandService.SubjectCommand(book, "GUTENBERG", "Gothic Fiction", "GUTENBERG", null, null, "SOURCE");
    assertEquals("CREATED", service.assign(other).get("result"));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM catalog.book_subject WHERE book_id=?", Integer.class, book));
  }

  @Test
  void unknownSourceIsRejected() {
    var command = new SubjectCommandService.SubjectCommand(UUID.randomUUID(), "OPEN_LIBRARY", "History", "MISSING", null, null, "SOURCE");
    assertThrows(SubjectCommandService.SubjectConflict.class, () -> service.assign(command));
  }
}
