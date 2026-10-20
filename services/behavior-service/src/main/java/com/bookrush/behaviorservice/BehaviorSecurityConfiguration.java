package com.bookrush.behaviorservice;

import java.util.Arrays;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@ConditionalOnProperty(name = "bookrush.security.enabled", havingValue = "true")
public class BehaviorSecurityConfiguration {
  @Bean
  SecurityFilterChain behaviorSecurity(HttpSecurity http,
      AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver) throws Exception {
    http.csrf(csrf -> csrf.disable()).cors(cors -> {})
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/internal/v1/recommendation/signals")
                .hasAuthority("SCOPE_bookrush.behavior.read")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(authenticationManagerResolver));
    return http.build();
  }

  @Bean
  AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
      JwtDecoder productDecoder,
      @Value("${bookrush.security.s2s-issuer}") String s2sIssuer,
      @Value("${bookrush.security.s2s-jwk-set-uri}") String s2sJwkSetUri,
      @Value("${bookrush.security.s2s-audience}") String s2sAudience) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    AuthenticationManager product = manager(productDecoder, converter);
    // Keycloak signs service tokens with RS256.  Make the accepted algorithm
    // explicit so Nimbus cannot select an encryption key (the same JWKS also
    // contains RSA-OAEP keys) when the platform realm rotates its keys.
    NimbusJwtDecoder s2sDecoder = NimbusJwtDecoder.withJwkSetUri(s2sJwkSetUri)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    OAuth2TokenValidator<Jwt> audience = token ->
        token.getAudience() != null && token.getAudience().contains(s2sAudience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "required audience is missing", null));
    s2sDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(s2sIssuer), audience));
    AuthenticationManager s2s = manager(s2sDecoder, converter);
    return request -> request.getRequestURI().startsWith("/api/internal/v1/recommendation/")
        ? s2s : product;
  }

  private static AuthenticationManager manager(JwtDecoder decoder,
      JwtAuthenticationConverter converter) {
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
    provider.setJwtAuthenticationConverter(converter);
    return new ProviderManager(provider);
  }

  @Bean(name = "corsConfigurationSource")
  CorsConfigurationSource cors(@Value("${bookrush.cors.allowed-origins}") String origins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
        .map(String::trim).filter(origin -> !origin.isBlank()).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of(
        "Authorization", "Accept", "Content-Type", "Idempotency-Key", "X-Admin-Client"));
    configuration.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
