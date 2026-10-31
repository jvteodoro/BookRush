package com.bookrush.readerstateservice;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
class PlantCorsConfiguration {
  @Bean
  FilterRegistrationBean<CorsFilter> plantCorsFilter(
      @Value("${BOOKRUSH_CORS_ALLOWED_ORIGINS:https://bookrush.jteodoro.tec.br,http://localhost:18080}") String origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/reader/plant", config);
    var registration = new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
