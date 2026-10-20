package com.bookrush.recommendationservice.taste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileBlendPolicyTest {
  private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

  @Test
  void coldStartUsesGlobalScoreAndConfidenceBlendsContinuously() {
    var policy = new ProfileBlendPolicy(new ProfileBlendPolicy.Config("blend-v1", .25, .75));
    var empty = new UserTasteProfile(UserTasteProfile.Status.EMPTY, null, "v1", "hash", 0, 0, 0, NOW);
    var low = new UserTasteProfile(UserTasteProfile.Status.READY, List.of(1d, 0d), "v1", "hash", 1, .5, .5, NOW);
    var established = new UserTasteProfile(UserTasteProfile.Status.READY, List.of(1d, 0d), "v1", "hash", 4, 1, .9, NOW);

    assertEquals(ProfileBlendPolicy.Level.EMPTY, policy.classify(empty).level());
    assertEquals(0.2, policy.blend(.2, .9, empty), 0.000001);
    assertEquals(.55, policy.blend(.2, .9, low), 0.000001);
    assertEquals(ProfileBlendPolicy.Level.ESTABLISHED, policy.classify(established).level());
    assertTrue(policy.blend(.2, .9, established) > policy.blend(.2, .9, low));
  }

  @Test
  void cacheExpiresAndInvalidatesOnlyTheRequestedSubject() {
    var cache = new UserTasteProfileCache(Duration.ofMinutes(1));
    var profile = new UserTasteProfile(UserTasteProfile.Status.READY, List.of(1d), "v1", "hash", 1, 1, .5, NOW);
    cache.put("user-a", profile, "bge-v1", NOW);
    cache.put("user-b", profile, "bge-v1", NOW);
    assertTrue(cache.get("user-a", "bge-v1", "hash", NOW.plusSeconds(1)).isPresent());
    cache.invalidate("user-a");
    assertTrue(cache.get("user-a", "bge-v1", "hash", NOW.plusSeconds(1)).isEmpty());
    assertTrue(cache.get("user-b", "bge-v1", "hash", NOW.plusSeconds(1)).isPresent());
    assertTrue(cache.get("user-b", "bge-v2", "hash", NOW.plusSeconds(1)).isEmpty());
    assertTrue(cache.get("user-b", "bge-v1", "hash", NOW.plusSeconds(61)).isEmpty());
  }
}
