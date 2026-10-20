package com.bookrush.recommendationservice.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PersonalizedFeedConfiguration.Properties.class)
public class PersonalizedFeedConfiguration {
  @Bean
  PersonalizedFeedGate personalizedFeedGate(Properties properties) {
    return new PersonalizedFeedGate(new PersonalizedFeedGate.Properties(
        properties.mode(), properties.minCoverage(), properties.minConfidence()));
  }

  @ConfigurationProperties(prefix = "feed.personalized")
  public record Properties(PersonalizedFeedGate.Mode mode, double minCoverage, double minConfidence) {
    public Properties {
      mode = mode == null ? PersonalizedFeedGate.Mode.OFF : mode;
      minCoverage = minCoverage < 0 ? .5 : minCoverage;
      minConfidence = minConfidence < 0 ? .5 : minConfidence;
    }
  }
}
