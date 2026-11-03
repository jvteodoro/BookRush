package com.bookrush.recommendationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.recommendationservice.ranking.PersonalizedFeedConfiguration;
import com.bookrush.recommendationservice.ranking.PersonalizedFeedGate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PersonalizedConfigurationContextTest {
  @Test
  void personalizedPropertiesBindAsARegularSpringConfigurationBean() {
    var source = new MapConfigurationPropertySource(java.util.Map.of(
        "feed.personalized.mode", "SHADOW",
        "feed.personalized.min-coverage", "0.7",
        "feed.personalized.min-confidence", "0.8",
        "feed.personalized.rollout-percentage", "25"));
    var properties = new Binder(java.util.List.of(source)).bind("feed.personalized",
        Bindable.of(Properties.class)).get();
    assertEquals(PersonalizedFeedGate.Mode.SHADOW, properties.mode());
    assertEquals(.7, properties.minCoverage());
    assertEquals(.8, properties.minConfidence());
    assertEquals(25, properties.rolloutPercentage());
  }

  private static final class Properties extends PersonalizedFeedConfiguration.Properties {}
}
