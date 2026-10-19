package com.bookrush.analytics.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

/** PostgreSQL session-level Flyway lock is required for CREATE INDEX CONCURRENTLY. */
@Configuration
public class FlywayPostgreSqlConfiguration implements FlywayConfigurationCustomizer {
  @Override
  public void customize(FluentConfiguration configuration) {
    configuration.configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
  }
}
