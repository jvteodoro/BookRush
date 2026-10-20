package com.bookrush.recommendationservice.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PersonalizedFeedConfiguration.Properties.class, BehaviorSignalsProperties.class})
public class PersonalizedFeedConfiguration {
  @Bean
  BehaviorSignalsClient behaviorSignalsClient(org.springframework.web.client.RestClient.Builder builder,
      BehaviorSignalsProperties properties, io.micrometer.core.instrument.MeterRegistry metrics) {
    return new BehaviorSignalsClient(builder, properties, metrics);
  }

  @Bean
  PersonalizedRankerV1 personalizedRanker() {
    return new PersonalizedRankerV1(new PersonalizedRankerV1.Config(
        PersonalizedRankerV1.MODEL_VERSION, .55, .25, 0, .10, .10,
        PersonalizedRankerV1.MissingPolicy.RENORMALIZE));
  }

  @Bean
  BookDiversityMmr bookDiversityMmr() {
    return new BookDiversityMmr(new BookDiversityMmr.Config("book-mmr-v1", .85));
  }

  @Bean
  ExplorationPolicy explorationPolicy() {
    return new ExplorationPolicy(new ExplorationPolicy.Config("exploration-v1", true, .10, 1));
  }

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
