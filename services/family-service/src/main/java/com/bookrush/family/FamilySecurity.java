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
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class FamilySecurity {
  static final String SUPERVISION_ADMIN = "/api/internal/v1/family/supervision/admin/";
  static final String SUPERVISION_LOOKUP = "/api/internal/v1/family/supervision/evaluate";
  static final String PRODUCT_LOOKUP = "/api/internal/v1/family/product-access/evaluate";
  static final String MEMBERSHIP_LOOKUP = "/api/internal/v1/family/membership/evaluate";

  @Bean
  SecurityFilterChain familyChain(HttpSecurity http, JwtDecoder decoder,
      @Value("${family.cors-origins}") String origins,
      @Value("${bookrush.security.issuer}") String productIssuer,
      @Value("${family.workload-issuer}") String workloadIssuer,
      @Value("${family.workload-jwks}") String workloadJwks,
      @Value("${family.membership-client}") String reader,
      @Value("${family.supervision-admin-client}") String administrator, ProductAccessWorkloads productAccess) throws Exception {
    if (productIssuer.equals(workloadIssuer) || reader.isBlank() || administrator.isBlank() || reader.equals(administrator)) {
      throw new IllegalArgumentException("family requires a separate workload issuer and an allowlisted client");
    }
    var product = provider(decoder);
    var workload = provider(workloadDecoder(workloadIssuer, workloadJwks, "bookrush-family"));
    var supervisionAdmin = provider(workloadDecoder(workloadIssuer, workloadJwks, "bookrush-family-supervision-admin"));
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    if (cors.getAllowedOrigins().contains("*")) {
      throw new IllegalArgumentException("family CORS requires explicit origins");
    }
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/family/**", cors);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(config -> config.configurationSource(source))
        .authorizeHttpRequests(routes -> routes
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/family/invite-address",
                "/api/v1/family/invitations/*/accept", "/api/v1/family/invitations/*/decline").hasAuthority("SCOPE_family.invites")
            .requestMatchers(HttpMethod.GET, "/api/v1/family/invitations/me").hasAuthority("SCOPE_family.invites")
            .requestMatchers(HttpMethod.POST, "/api/v1/family", "/api/v1/family/*/invitations")
                .hasAuthority("SCOPE_family.manage")
            .requestMatchers(HttpMethod.GET, "/api/v1/family/me", "/api/v1/family/*/invitations",
                "/api/v1/family/*/members", "/api/v1/family/*/members/*/publication")
                .hasAuthority("SCOPE_family.manage")
            .requestMatchers(HttpMethod.PUT, "/api/v1/family/*/members/*/state", "/api/v1/family/*/members/*/publication")
                .hasAuthority("SCOPE_family.manage")
            .requestMatchers(HttpMethod.POST, "/api/v1/family/supervision").hasAuthority("SCOPE_supervision.request")
            .requestMatchers(HttpMethod.GET, "/api/v1/family/supervision/me").hasAuthority("SCOPE_supervision.request")
            .requestMatchers(HttpMethod.POST, ProfileDiscoveryFacts.PATH)
                .access((auth, route) -> new AuthorizationDecision(auth.get() instanceof JwtAuthenticationToken token
                    && ProfileDiscoveryFacts.isConsumer(token.getToken().getClaimAsString("azp"))
                    && productAccess.contains(token.getToken().getClaimAsString("azp"))
                    && token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_product-access.evaluate"))))
            .requestMatchers(HttpMethod.POST, ProductAccessController.PATH)
                .access((auth, route) -> new AuthorizationDecision(auth.get() instanceof JwtAuthenticationToken token
                    && productAccess.contains(token.getToken().getClaimAsString("azp"))
                    && token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_product-access.evaluate"))))
            .requestMatchers(HttpMethod.POST, "/api/internal/v1/family/supervision/history-window")
                .access((auth, context) -> new AuthorizationDecision(auth.get() instanceof JwtAuthenticationToken token
                    && productAccess.contains(token.getToken().getClaimAsString("azp"))
                    && token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_product-access.evaluate"))))
            .requestMatchers(HttpMethod.POST, "/api/internal/v1/product-access/session")
                .access(client("bookrush-reader-bff-session-s2s", "product-access.evaluate"))
            .requestMatchers(HttpMethod.POST, MEMBERSHIP_LOOKUP).access(client(reader, "family.membership.read"))
            .requestMatchers(HttpMethod.POST, SUPERVISION_LOOKUP, PRODUCT_LOOKUP)
                .access(client(reader, "supervision.evaluate"))
            .requestMatchers(HttpMethod.POST, SUPERVISION_ADMIN + "*").access(client(administrator, "supervision.manage"))
            .requestMatchers(HttpMethod.DELETE, "/api/v1/family/*/invitations/*", "/api/v1/family/*/members/*")
                .hasAuthority("SCOPE_family.manage")
            .anyRequest().denyAll())
        .exceptionHandling(errors -> errors
            .defaultAuthenticationEntryPointFor((request, response, failure) -> ProductAccessController.problem(response,
                401, "INVALID_AUTHENTICATION", ProductAccessController.headerCorrelation(request)), FamilySecurity::decisionRequest)
            .defaultAccessDeniedHandlerFor((request, response, failure) -> ProductAccessController.problem(response,
                403, "CALLER_FORBIDDEN", ProductAccessController.headerCorrelation(request)), FamilySecurity::decisionRequest))
        .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint((request, response, failure) -> {
          if (decisionRequest(request)) {
            ProductAccessController.problem(response, 401, "INVALID_AUTHENTICATION", ProductAccessController.headerCorrelation(request));
          } else {
            new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint()
                .commence(request, response, failure);
          }
        }).authenticationManagerResolver(request -> {
          String path = request.getRequestURI().substring(request.getContextPath().length());
          if (path.startsWith(SUPERVISION_ADMIN)) {
            return supervisionAdmin::authenticate;
          }
          return path.startsWith("/api/internal/") ? workload::authenticate : product::authenticate;
        }))
        .build();
  }

  private static boolean decisionRequest(jakarta.servlet.http.HttpServletRequest request) {
    return request.getMethod().equals("POST")
        && request.getRequestURI().substring(request.getContextPath().length()).equals(ProductAccessController.PATH);
  }

  private static JwtDecoder workloadDecoder(String issuer, String jwks, String requiredAudience) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), jwt ->
            jwt.getAudience() != null && jwt.getAudience().contains(requiredAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience mismatch", null))));
    return decoder;
  }

  private static org.springframework.security.authorization.AuthorizationManager<
      org.springframework.security.web.access.intercept.RequestAuthorizationContext> client(String client, String scope) {
    return (auth, context) -> new AuthorizationDecision(auth.get() instanceof JwtAuthenticationToken user
        && client.equals(user.getToken().getClaimAsString("azp"))
        && user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + scope)));
  }

  private static JwtAuthenticationProvider provider(JwtDecoder decoder) {
    return new JwtAuthenticationProvider(value -> {
      var token = decoder.decode(value);
      if (token.getSubject() == null || token.getSubject().isBlank()) {
        throw new BadJwtException("missing token subject");
      }
      return token;
    });
  }
}
