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
  PersonalizedDecisionReplay personalizedDecisionReplay() {
    return new PersonalizedDecisionReplay();
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
        properties.mode(), properties.minCoverage(), properties.minConfidence(), properties.rolloutPercentage()));
  }

  @ConfigurationProperties(prefix = "feed.personalized")
  public static class Properties {
    private PersonalizedFeedGate.Mode mode = PersonalizedFeedGate.Mode.OFF;
    private double minCoverage = .5;
    private double minConfidence = .5;
    private double rolloutPercentage = 100;

    public Properties() {}

    public Properties(PersonalizedFeedGate.Mode mode, double minCoverage, double minConfidence) {
      this.mode = mode;
      this.minCoverage = minCoverage;
      this.minConfidence = minConfidence;
    }

    public PersonalizedFeedGate.Mode mode() { return mode; }
    public double minCoverage() { return minCoverage; }
    public double minConfidence() { return minConfidence; }
    public double rolloutPercentage() { return rolloutPercentage; }

    public void setMode(PersonalizedFeedGate.Mode mode) {
      this.mode = mode == null ? PersonalizedFeedGate.Mode.OFF : mode;
    }

    public void setMinCoverage(double value) {
      this.minCoverage = value < 0 ? .5 : value;
    }

    public void setMinConfidence(double value) {
      this.minConfidence = value < 0 ? .5 : value;
    }

    public void setRolloutPercentage(double value) {
      this.rolloutPercentage = value < 0 ? 100 : value;
    }
  }
}
