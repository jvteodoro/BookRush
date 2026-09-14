package com.bookrush.catalog.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CanonicalCommandServiceIT {
  @Autowired CanonicalCommandService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  @Test
  void replayIsNoOpAndProvenanceKeepsPreviousValue() {
    var operation = "openlibrary/work/OL" + UUID.randomUUID() + "/canonicalize/v1";
    var metadata = mapper.createObjectNode();
    var command = new CanonicalCommand(operation, "a".repeat(64), 0, "GUTENBERG", "GUTENBERG_ID",
        Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()) + 1), "Fixture title", "en",
        "First description", metadata, null, null, "Fixture Author", "AUTHOR");

    var created = service.apply(command);
    assertEquals("CREATED", created.get("result"));
    var firstCount = jdbc.queryForObject("SELECT count(*) FROM catalog.field_provenance WHERE entity_id=?", Integer.class, created.get("bookId"));
    assertEquals(2, firstCount);
    var replay = service.apply(command);
    assertEquals(created.get("bookId").toString(), replay.get("bookId").toString());
    assertEquals(created.get("editionId").toString(), replay.get("editionId").toString());
    assertEquals(created.get("result"), replay.get("result"));
    assertEquals(firstCount, jdbc.queryForObject("SELECT count(*) FROM catalog.field_provenance WHERE entity_id=?", Integer.class, created.get("bookId")));
    assertThrows(CanonicalCommandService.CanonicalConflict.class, () -> service.apply(
        new CanonicalCommand(operation, "b".repeat(64), 0, command.sourceCode(), command.identifierType(),
            command.identifierValue(), command.title(), command.language(), command.description(), metadata,
            null, null, command.authorName(), command.authorRole())));
    assertEquals("GUTENBERG_CANONICAL", jdbc.queryForObject(
        "SELECT rule_code FROM catalog.field_provenance WHERE entity_id=? AND field_name='canonical_title'", String.class, created.get("bookId")));
  }
}
