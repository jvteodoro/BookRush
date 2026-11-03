package com.bookrush.recommendationservice.ranking;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AnalyticsRankingProperties.class)
public class RankingConfiguration {
  @Bean
  AnalyticsScoreClient analyticsScoreClient(RestClient.Builder builder,
      AnalyticsRankingProperties properties, MeterRegistry metrics) {
    return new AnalyticsScoreClient(builder, properties, metrics);
  }
}
