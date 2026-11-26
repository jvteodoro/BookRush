package com.bookrush.socialservice;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
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
public class SocialSecurityConfiguration {
  @Bean
  @Order(0)
  @ConditionalOnProperty(name = "bookrush.security.product-access.enabled", havingValue = "true")
  SecurityFilterChain moderationSecurity(HttpSecurity http,
      @Value("${bookrush.social.admin-issuer}") String issuer,
      @Value("${bookrush.social.admin-jwks}") String jwks,
      @Value("${bookrush.social.admin-audience}") String audience,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String productIssuer) throws Exception {
    if (issuer.equals(productIssuer) || issuer.isBlank() || jwks.isBlank() || audience.isBlank()) {
      throw new IllegalStateException("Social moderation requires a distinct configured platform realm");
    }
    return http.securityMatcher("/api/v1/social/moderation/**")
        .csrf(csrf -> csrf.disable()).cors(cors -> {})
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/v1/social/moderation/reports")
                .hasAnyRole("platform-admins", "operators", "OPERATOR")
            .requestMatchers(HttpMethod.PUT, "/api/v1/social/moderation/comments/*")
                .hasAnyRole("platform-admins", "operators", "OPERATOR")
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
            jwt.decoder(realmDecoder(jwks, issuer, audience)).jwtAuthenticationConverter(groupsConverter())))
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "bookrush.security.enabled", havingValue = "true")
  @Order(1)
  SecurityFilterChain socialSecurity(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
    http.csrf(csrf -> csrf.disable()).cors(cors -> {})
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(groupsConverter())));
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(
      name = "bookrush.security.enabled", havingValue = "false", matchIfMissing = true)
  SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "bookrush.security.enabled", havingValue = "true")
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${bookrush.security.audience}") String audience) {
    return realmDecoder(jwkSetUri, issuer, audience);
  }

  private static JwtDecoder realmDecoder(String jwkSetUri, String issuer, String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
    OAuth2TokenValidator<Jwt> audienceValidator = token ->
        token.getAudience() != null && token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "required audience is missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
    return decoder;
  }

  private JwtAuthenticationConverter groupsConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      var authorities = new java.util.ArrayList<GrantedAuthority>();
      var scopes = jwt.getClaimAsString("scope");
      if (scopes != null) for (String scope : scopes.split("\\s+"))
        if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
      var groups = jwt.getClaimAsStringList("groups");
      if (groups != null) groups.forEach(group -> authorities.add(new SimpleGrantedAuthority("ROLE_" + group)));
      var realm = jwt.getClaimAsMap("realm_access");
      if (realm != null && realm.get("roles") instanceof java.util.Collection<?> roles && roles.contains("OPERATOR")) {
        authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
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
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
