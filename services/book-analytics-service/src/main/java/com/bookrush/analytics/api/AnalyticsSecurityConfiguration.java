package com.bookrush.analytics.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AnalyticsSecurityConfiguration {
  @Bean SecurityFilterChain security(HttpSecurity http, @Value("${bookrush.security.enabled:false}") boolean enabled) throws Exception {
    http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> {
      auth.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
      if (enabled) auth.requestMatchers("/api/admin/**", "/api/internal/**").hasAuthority("SCOPE_bookrush.analytics").anyRequest().authenticated();
      else auth.anyRequest().permitAll();
    });
    if (enabled) http.oauth2ResourceServer(oauth -> oauth.jwt());
    return http.build();
  }
}
