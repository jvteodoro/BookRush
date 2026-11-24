package com.bookrush.family;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class FamilySecurity {
  @Bean
  SecurityFilterChain familyChain(HttpSecurity http, JwtDecoder decoder,
      @Value("${family.cors-origins}") String origins) throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    if (cors.getAllowedOrigins().contains("*")) {
      throw new IllegalArgumentException("family CORS requires explicit origins");
    }
    cors.setAllowedMethods(List.of("GET", "POST", "DELETE"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/family/**", cors);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(config -> config.configurationSource(source))
        .authorizeHttpRequests(routes -> routes
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/family/invite-address").hasAuthority("SCOPE_family.invites")
            .requestMatchers(HttpMethod.GET, "/api/v1/family/invitations/me").hasAuthority("SCOPE_family.invites")
            .requestMatchers(HttpMethod.POST, "/api/v1/family", "/api/v1/family/*/invitations")
                .hasAuthority("SCOPE_family.manage")
            .requestMatchers(HttpMethod.GET, "/api/v1/family/me", "/api/v1/family/*/invitations")
                .hasAuthority("SCOPE_family.manage")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/family/*/invitations/*")
                .hasAuthority("SCOPE_family.manage")
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(value -> {
          var token = decoder.decode(value);
          if (token.getSubject() == null || token.getSubject().isBlank()) {
            throw new BadJwtException("missing token subject");
          }
          return token;
        })))
        .build();
  }
}
