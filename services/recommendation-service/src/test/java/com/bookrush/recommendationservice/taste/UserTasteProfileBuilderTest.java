package com.bookrush.recommendationservice.taste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTasteProfileBuilderTest {
  private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");
  private static final UserTasteProfileBuilder.Config CONFIG =
      new UserTasteProfileBuilder.Config("bootstrap-v1", Duration.ofDays(30), 50, 4, 3);

  @Test
  void buildsNormalizedDirectionFromObservedPositiveSignals() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var embeddings = Map.of(first, embedding(first, List.of(1d, 0d)),
        second, embedding(second, List.of(0d, 1d)));
    var signals = List.of(new UserTasteProfileBuilder.Signal(first, 20, 1, 1, 0, 100, true, false, NOW),
        new UserTasteProfileBuilder.Signal(second, 1, 1, 0, 0, 0, false, false, NOW));

    var profile = new UserTasteProfileBuilder(CONFIG).build(signals, embeddings, NOW);

    assertEquals(UserTasteProfile.Status.READY, profile.status());
    assertEquals(2, profile.effectiveSignalCount());
    assertEquals(1d, profile.vector().stream().mapToDouble(value -> value * value).sum(), 0.000001);
    assertEquals(CONFIG.hash(), profile.configurationHash());
  }

  @Test
  void impressionsOnlyOrReversalProducesEmptyProfile() {
    UUID book = UUID.randomUUID();
    var signal = new UserTasteProfileBuilder.Signal(book, 50, 0, 0, 0, 0, false, false, NOW);
    var profile = new UserTasteProfileBuilder(CONFIG).build(List.of(signal),
        Map.of(book, embedding(book, List.of(1d, 0d))), NOW);

    assertEquals(UserTasteProfile.Status.EMPTY, profile.status());
    assertNull(profile.vector());
    assertEquals(0, profile.effectiveSignalCount());
  }

  @Test
  void rejectsStaleOrMismatchedEmbeddingWithoutChangingProfileMeaning() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var signals = List.of(
        new UserTasteProfileBuilder.Signal(first, 0, 1, 1, 0, 100, true, false, NOW),
        new UserTasteProfileBuilder.Signal(second, 0, 1, 1, 0, 100, true, false, NOW));
    var embeddings = Map.of(first, embedding(first, List.of(1d, 0d)),
        second, new UserTasteProfileBuilder.BookEmbedding(second, "", "", UUID.randomUUID(), "bad", List.of(0d, 1d)));

    var profile = new UserTasteProfileBuilder(CONFIG).build(signals, embeddings, NOW);

    assertEquals(1, profile.effectiveSignalCount());
    assertEquals(0.5d, profile.coverage(), 0.000001);
    assertEquals(1d, profile.vector().getFirst(), 0.000001);
  }

  @Test
  void replayIsDeterministicAndDifferentSignalsPointToDifferentBooks() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var embeddings = Map.of(first, embedding(first, List.of(1d, 0d)),
        second, embedding(second, List.of(0d, 1d)));
    var builder = new UserTasteProfileBuilder(CONFIG);
    var firstSignals = List.of(new UserTasteProfileBuilder.Signal(first, 0, 1, 1, 0, 100, true, false, NOW));
    var secondSignals = List.of(new UserTasteProfileBuilder.Signal(second, 0, 1, 1, 0, 100, false, true, NOW));

    var firstRun = builder.build(firstSignals, embeddings, NOW);
    var replay = builder.build(firstSignals, embeddings, NOW);
    var secondRun = builder.build(secondSignals, embeddings, NOW);

    assertEquals(firstRun, replay);
    assertEquals(1d, firstRun.vector().getFirst(), 0.000001);
    assertEquals(1d, secondRun.vector().get(1), 0.000001);
    assertEquals(0d, firstRun.vector().get(1), 0.000001);
  }

  private static UserTasteProfileBuilder.BookEmbedding embedding(UUID book, List<Double> vector) {
    return new UserTasteProfileBuilder.BookEmbedding(book, "bge-m3", "2026-01",
        UUID.randomUUID(), "a".repeat(64), vector);
  }
}
