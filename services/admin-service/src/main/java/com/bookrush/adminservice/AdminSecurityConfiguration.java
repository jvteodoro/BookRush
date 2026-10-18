package com.bookrush.adminservice;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@ConditionalOnProperty(name = "bookrush.security.enabled", havingValue = "true")
public class AdminSecurityConfiguration {
  @Bean
  SecurityFilterChain adminSecurity(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
    http.csrf(csrf -> csrf.disable()).cors(cors -> {})
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(groupsConverter())));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${bookrush.security.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuer);
    OAuth2TokenValidator<Jwt> audienceValidator = token ->
        token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "required audience is missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
    return decoder;
  }

  JwtAuthenticationConverter groupsConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      var authorities = new java.util.ArrayList<GrantedAuthority>();
      var scopes = jwt.getClaimAsString("scope");
      if (scopes != null) for (String scope : scopes.split("\\s+"))
        if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
      var groups = jwt.getClaimAsStringList("groups");
      if (groups != null) groups.forEach(group -> authorities.add(new SimpleGrantedAuthority("ROLE_" + group)));
      Object realmAccess = jwt.getClaim("realm_access");
      if (realmAccess instanceof Map<?, ?> access && access.get("roles") instanceof List<?> roles) {
        roles.stream().filter(String.class::isInstance).map(String.class::cast)
            .filter(role -> !role.isBlank())
            .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
      }
      return authorities;
    });
    return converter;
  }

  @Bean(name = "corsConfigurationSource")
  CorsConfigurationSource cors(@Value("${bookrush.cors.allowed-origins}") String origins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Accept", "Content-Type", "Idempotency-Key", "X-Admin-Client"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
