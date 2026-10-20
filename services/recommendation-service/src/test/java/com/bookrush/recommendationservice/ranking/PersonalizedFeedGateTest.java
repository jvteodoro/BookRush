package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.recommendationservice.taste.UserTasteProfile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalizedFeedGateTest {
  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
  private static final UserTasteProfile READY = new UserTasteProfile(
      UserTasteProfile.Status.READY, List.of(1d), "v1", "hash", 2, .9, .9, NOW);

  @Test
  void onUsesPersonalizedOnlyWhenAllPrerequisitesAreReady() {
    var gate = new PersonalizedFeedGate(new PersonalizedFeedGate.Properties(
        PersonalizedFeedGate.Mode.ON, .5, .5));
    assertEquals(PersonalizedFeedGate.Path.PERSONALIZED, gate.decide(READY, true, true).path());
    assertEquals("profile-missing", gate.decide(null, true, true).reason());
    assertEquals("behavior-unavailable", gate.decide(READY, false, true).reason());
    assertEquals("snapshot-unavailable", gate.decide(READY, true, false).reason());
  }

  @Test
  void offAndShadowNeverChangeTheControlFeed() {
    assertEquals(PersonalizedFeedGate.Path.FALLBACK,
        new PersonalizedFeedGate(new PersonalizedFeedGate.Properties(
            PersonalizedFeedGate.Mode.OFF, 0, 0)).decide(READY, true, true).path());
    assertEquals(PersonalizedFeedGate.Path.SHADOW,
        new PersonalizedFeedGate(new PersonalizedFeedGate.Properties(
            PersonalizedFeedGate.Mode.SHADOW, 0, 0)).decide(READY, true, true).path());
  }
}
