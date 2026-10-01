package com.bookrush.publisherservice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Keeps the ingestion callback private to its shared-secret controller check. */
@Configuration
@ConditionalOnProperty(prefix = "bookrush.security", name = "enabled", havingValue = "true")
public class PublisherSecurityConfiguration {
  @Bean
  @Order(1)
  SecurityFilterChain publisherSecurity(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/internal/v1/publisher/submissions/**").permitAll()
                    .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt());
    return http.build();
  }
}
