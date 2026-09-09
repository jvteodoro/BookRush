package com.bookrush.ingestion.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

@Configuration
@EnableConfigurationProperties(IngestionSecurityProperties.class)
public class IngestionSecurityConfiguration {
  @Bean
  @ConditionalOnProperty(prefix = "ingestion.security", name = "enabled", havingValue = "true")
  JwtDecoder ingestionJwtDecoder(IngestionSecurityProperties properties) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.audience())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience mismatch", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
    return decoder;
  }

  @Bean
  @ConditionalOnProperty(prefix = "ingestion.security", name = "enabled", havingValue = "true")
  SecurityFilterChain ingestionSecurity(HttpSecurity http, IngestionSecurityProperties properties) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ingestion-docs/**").permitAll()
            .requestMatchers("/admin/v1/ingestion/**").hasAnyRole("OPERATOR", "REVIEWER", "CLEANUP")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())));
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "ingestion.security", name = "enabled", havingValue = "false", matchIfMissing = true)
  SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
    // Keep local Compose usable when OIDC is intentionally disabled. Without
    // this chain Spring Security installs a generated-password chain that
    // protects Swagger and every controller unexpectedly.
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ingestion-docs/**").permitAll()
            .anyRequest().denyAll())
        .build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> rolesConverter() {
    var converter = new JwtAuthenticationConverter();
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
