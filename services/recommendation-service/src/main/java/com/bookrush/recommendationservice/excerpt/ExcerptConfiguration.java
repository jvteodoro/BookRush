package com.bookrush.recommendationservice.excerpt;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExcerptPolicyProperties.class)
public class ExcerptConfiguration {
  @Bean
  AnalyticsExcerptClient analyticsExcerptClient(RestClient.Builder builder, ExcerptPolicyProperties policy,
      MeterRegistry metrics) {
    return new AnalyticsExcerptClient(builder, policy, metrics);
  }

  @Bean(destroyMethod = "close")
  ExcerptEnrichmentService excerptEnrichmentService(AnalyticsExcerptClient client,
      ExcerptPolicyProperties policy, MeterRegistry metrics) {
    return new ExcerptEnrichmentService(client, policy, metrics);
  }
}
