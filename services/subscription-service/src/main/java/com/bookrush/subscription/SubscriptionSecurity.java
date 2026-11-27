package com.bookrush.subscription;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class SubscriptionSecurity {
  static final String CHECKOUT = "/api/v1/subscription/checkout";
  static final String CANCEL = "/api/v1/subscription/cancel";
  static final String MOCK_SIMULATE = "/api/internal/v1/subscription/billing/mock/simulate";
  static final String EVALUATE = "/api/internal/v1/subscription/entitlements/evaluate";
  static final String BILLING_EVENT = "/api/internal/v1/subscription/billing/events";
  static final String TRANSITION = "/api/internal/v1/subscription/transitions";

  @Bean
  SecurityFilterChain subscriptionChain(HttpSecurity http, JwtDecoder productDecoder,
      @Value("${subscription.workload-issuer}") String issuer,
      @Value("${bookrush.security.issuer}") String productIssuer,
      @Value("${subscription.workload-jwks}") String jwks,
      @Value("${subscription.evaluation-client}") String evaluator,
      @Value("${subscription.transition-client}") String writer,
      @Value("${subscription.cors-origins}") String origins) throws Exception {
    if (issuer.equals(productIssuer) || evaluator.equals(writer) || evaluator.isBlank() || writer.isBlank()) {
      throw new IllegalArgumentException("subscription requires separate issuers and workload clients");
    }
    var workloadDecoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    workloadDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), jwt ->
            jwt.getAudience() != null && jwt.getAudience().contains("bookrush-subscription")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience mismatch", null))));
    var product = authenticatedSubject(productDecoder);
    var workload = authenticatedSubject(workloadDecoder);
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    if (cors.getAllowedOrigins().contains("*")) {
      throw new IllegalArgumentException("subscription CORS requires explicit origins");
    }
    cors.addAllowedMethod("GET");
    cors.addAllowedMethod("POST");
    cors.addAllowedHeader("Idempotency-Key");
    cors.addAllowedHeader("Authorization");
    var corsSource = new UrlBasedCorsConfigurationSource();
    corsSource.registerCorsConfiguration("/api/v1/subscription/**", cors);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(c -> c.configurationSource(corsSource))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/subscription/me", "/api/v1/subscription/me/entitlements")
                .hasAuthority("SCOPE_subscription.read")
            .requestMatchers(HttpMethod.POST, EVALUATE).access(client(evaluator, "entitlements.evaluate"))
            .requestMatchers(HttpMethod.POST, CHECKOUT).hasAuthority("SCOPE_subscription.read")
            .requestMatchers(HttpMethod.POST, CANCEL).hasAuthority("SCOPE_subscription.read")
            .requestMatchers(HttpMethod.POST, TRANSITION, BILLING_EVENT, MOCK_SIMULATE).access(client(writer, "subscription.transitions.write"))
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(request ->
            request.getRequestURI().startsWith(request.getContextPath() + "/api/internal/") ? workload::authenticate : product::authenticate))
        .build();
  }

  private static JwtAuthenticationProvider authenticatedSubject(JwtDecoder decoder) {
    return new JwtAuthenticationProvider(value -> {
      var token = decoder.decode(value);
      if (token.getSubject() == null || token.getSubject().isBlank()) {
        throw new BadJwtException("missing token subject");
      }
      return token;
    });
  }

  private AuthorizationManager<RequestAuthorizationContext> client(String clientId, String scope) {
    return (authentication, request) -> new AuthorizationDecision(
        authentication.get() instanceof JwtAuthenticationToken token
        && clientId.equals(token.getToken().getClaimAsString("azp"))
        && token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + scope)));
  }
}
