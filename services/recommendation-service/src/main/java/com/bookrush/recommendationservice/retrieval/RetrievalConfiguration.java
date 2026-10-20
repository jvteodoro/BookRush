package com.bookrush.recommendationservice.retrieval;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CandidateRetrievalProperties.class)
public class RetrievalConfiguration {
  @Bean
  CandidateRetriever candidateRetriever(RestClient.Builder builder,
      @Value("${bookrush.catalog-url:http://catalog-service:8080}") String catalogUrl,
      CandidateRetrievalProperties properties, MeterRegistry metrics) {
    return new CatalogCandidateRetriever(builder, catalogUrl, properties, metrics);
  }
}
