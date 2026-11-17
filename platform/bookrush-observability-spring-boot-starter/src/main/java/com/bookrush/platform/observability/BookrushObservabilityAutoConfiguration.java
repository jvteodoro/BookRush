package com.bookrush.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "bookrush.observability.correlation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BookrushObservabilityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  CorrelationIdFilter bookrushCorrelationIdFilter() {
    return new CorrelationIdFilter();
  }

  @Bean
  @ConditionalOnMissingBean(name = "bookrushCommonMeterTags")
  MeterFilter bookrushCommonMeterTags(Environment environment) {
    String service = environment.getProperty("spring.application.name", "bookrush");
    String deployment = environment.getProperty("bookrush.observability.environment", "unknown");
    return MeterFilter.commonTags(List.of(
        io.micrometer.core.instrument.Tag.of("service", safe(service)),
        io.micrometer.core.instrument.Tag.of("environment", safe(deployment))));
  }

  @Bean
  @ConditionalOnMissingBean(name = "bookrushHttpHistogram")
  MeterFilter bookrushHttpHistogram() {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(
          Meter.Id id, DistributionStatisticConfig config) {
        if (!"http.server.requests".equals(id.getName())) return config;
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .serviceLevelObjectives(
                0.1, 0.25, 0.5, 1.0, 2.0, 5.0)
            .build()
            .merge(config);
      }
    };
  }

  private static String safe(String value) {
    if (value == null || value.isBlank()) return "unknown";
    return value.replaceAll("[^a-zA-Z0-9_.-]", "_");
  }
}
