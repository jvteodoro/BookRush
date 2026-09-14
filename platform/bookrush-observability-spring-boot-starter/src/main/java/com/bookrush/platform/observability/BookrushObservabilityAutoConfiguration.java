package com.bookrush.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "bookrush.observability.correlation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookrushObservabilityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  CorrelationIdFilter bookrushCorrelationIdFilter() { return new CorrelationIdFilter(); }
}
