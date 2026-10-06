package com.bookrush.recommendationservice.excerpt;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExcerptSelectionTest {
  private static final UUID BOOK_VERSION = UUID.randomUUID();
  private static Map<String, Object> row(String id, Object score, boolean eligible) {
    var row = new java.util.HashMap<String, Object>();
    row.put("id", UUID.fromString(id));
    row.put("source_asset_version_id", BOOK_VERSION);
    row.put("text", "A valid body excerpt.");
    row.put("text_sha256", "0123456789012345678901234567890123456789012345678901234567890123");
    row.put("start_codepoint", 10);
    row.put("end_codepoint", 30);
    row.put("generation_method", "SENTENCE_WINDOW");
    row.put("generator_version", "v1");
    if (score != null) row.put("candidate_score", score);
    row.put("ranker_version", "rank-v1");
    row.put("body_eligible", eligible);
    return row;
  }

  @Test
  void prefersRankAndRejectsStructuralRows() {
    var p = new ExcerptPolicyProperties(true, 100, "", 1600, 1, true,
        Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(1),
        Duration.ofSeconds(1), 2, "http://analytics", "http://token", "id", "secret", "aud", "scope");
    var selected = ExcerptSelection.select(List.of(
        row(UUID.randomUUID().toString(), 0.2, true),
        row(UUID.randomUUID().toString(), 0.9, true),
        row(UUID.randomUUID().toString(), 1.0, false)), p);
    assertNotNull(selected);
    assertEquals(0.9, selected.rank().score());
  }

  @Test
  void unprocessedBookHasNoExcerptAndMissingRankStaysNull() {
    var p = new ExcerptPolicyProperties(true, 100, "", 1600, 1, true,
        Duration.ofMinutes(1), Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(1),
        Duration.ofSeconds(1), 2, "http://analytics", "http://token", "id", "secret", "aud", "scope");
    assertNull(ExcerptSelection.select(List.of(), p));
    var row = new java.util.HashMap<>(row(UUID.randomUUID().toString(), null, true));
    row.remove("candidate_score");
    row.remove("ranker_version");
    FeedExcerpt selected = ExcerptSelection.select(List.of(row), p);
    assertNotNull(selected);
    assertNull(selected.rank());
  }
}
