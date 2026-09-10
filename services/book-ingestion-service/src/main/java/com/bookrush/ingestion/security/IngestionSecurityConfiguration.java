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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
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
    OAuth2TokenValidator<Jwt> audience = token -> token.getAudience() != null
        && token.getAudience().contains(properties.audience())
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
            .requestMatchers("/api/admin/v1/ingestion/run", "/api/admin/v1/ingestion/jobs/*/cancel", "/api/admin/v1/ingestion/jobs/*/resume").hasAnyRole("OPERATOR", "CLEANUP")
            .requestMatchers("/api/admin/v1/ingestion/**").hasAnyRole("OPERATOR", "REVIEWER", "CLEANUP")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())));
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "ingestion.security", name = "enabled", havingValue = "false", matchIfMissing = true)
  SecurityFilterChain localSecurity(HttpSecurity http, IngestionSecurityProperties properties) throws Exception {
    var fixture = new OncePerRequestFilter() {
      protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
        String supplied = request.getHeader("X-BookRush-Fixture-Token");
        if (properties.localFixtureMode() && properties.localFixtureToken() != null && !properties.localFixtureToken().isBlank()
            && supplied != null && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), properties.localFixtureToken().getBytes(StandardCharsets.UTF_8))) {
          var auth = new UsernamePasswordAuthenticationToken("fixture", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"), new SimpleGrantedAuthority("ROLE_REVIEWER"), new SimpleGrantedAuthority("ROLE_CLEANUP")));
          org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
      }
    };
    return http.csrf(csrf -> csrf.disable())
        .addFilterBefore(fixture, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ingestion-docs/**").permitAll()
            .requestMatchers("/api/admin/v1/ingestion/**").hasAnyRole("OPERATOR", "REVIEWER", "CLEANUP")
            .anyRequest().denyAll())
        .build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> rolesConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
      List<GrantedAuthority> result = new ArrayList<>();
      Object roles = jwt.getClaims().get("roles");
      if (roles instanceof List<?> values) values.forEach(value -> result.add(new SimpleGrantedAuthority("ROLE_" + value)));
      Object realm = jwt.getClaims().get("realm_access");
      if (realm instanceof java.util.Map<?, ?> map && map.get("roles") instanceof List<?> values) values.forEach(value -> result.add(new SimpleGrantedAuthority("ROLE_" + value)));
      return result;
    });
    return converter;
  }
}
