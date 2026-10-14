package com.bookrush.catalog.asset;

import com.bookrush.catalog.canonical.CanonicalServiceProperties;
import com.bookrush.catalog.storage.StorageConfiguration;
import com.bookrush.catalog.storage.StorageProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@Import(StorageConfiguration.class)
@EnableConfigurationProperties({
  CanonicalServiceProperties.class,
  CatalogOidcProperties.class,
  CatalogProductOidcProperties.class,
  CatalogCorsProperties.class
})
public class AssetSecurity {
  private static final Logger log = LoggerFactory.getLogger(AssetSecurity.class);

  @Bean
  org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers() {
    return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
  }

  @Bean
  @Order(0)
  SecurityFilterChain productThumbnailSecurity(
      HttpSecurity http, CatalogProductOidcProperties productOidc) throws Exception {
    return http
        .securityMatcher(
            new AntPathRequestMatcher("/api/v1/books/*/thumbnail", HttpMethod.GET.name()))
        .csrf(c -> c.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth.jwt(
                    jwt ->
                        jwt.decoder(productJwtDecoder(productOidc))
                            .jwtAuthenticationConverter(catalogRolesConverter())))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (request, response, error) -> {
                      log.warn(
                          "thumbnail authorization denied path={} principal={} authorities={}",
                          request.getRequestURI(),
                          request.getUserPrincipal() == null
                              ? "anonymous"
                              : request.getUserPrincipal().getName(),
                          SecurityContextHolder.getContext().getAuthentication() == null
                              ? List.of()
                              : SecurityContextHolder.getContext().getAuthentication().getAuthorities());
                      response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }))
        .build();
  }

  @Bean
  JwtDecoder productJwtDecoder(CatalogProductOidcProperties productOidc) {
    if (!productOidc.enabled() || productOidc.jwkSetUri().isBlank()) {
      throw new IllegalStateException("Product OIDC must be configured for protected thumbnails");
    }
    var decoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(productOidc.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(productOidc.issuer());
    OAuth2TokenValidator<Jwt> audience =
        token ->
            token.getAudience().contains(productOidc.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "product audience mismatch", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
    return decoder;
  }

  JwtDecoder catalogJwtDecoder(CatalogOidcProperties oidc) {
    if (!oidc.enabled() || oidc.jwkSetUri().isBlank()) {
      throw new IllegalStateException("Catalog OIDC must be configured");
    }
    var decoder = org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(oidc.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(oidc.issuer());
    OAuth2TokenValidator<Jwt> audience =
        token ->
            token.getAudience().contains(oidc.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "catalog audience mismatch", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
    return decoder;
  }

  @Bean
  @Order(1)
  SecurityFilterChain security(
      HttpSecurity http,
      StorageProperties props,
      CanonicalServiceProperties canonical,
      CatalogOidcProperties oidc)
      throws Exception {
    var filter =
        new OncePerRequestFilter() {
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            String auth = request.getHeader("Authorization");
            // Internal service calls may use a dedicated header so a gateway cannot
            // accidentally reinterpret the service credential as a user bearer token.
            String serviceToken = request.getHeader("X-Canonical-Service-Token");
            String readerToken = request.getHeader("X-Content-Service-Token");
            if (!oidc.readerInternalToken().isBlank()
                && readerToken != null
                && MessageDigest.isEqual(readerToken.getBytes(StandardCharsets.UTF_8), oidc.readerInternalToken().getBytes(StandardCharsets.UTF_8))) {
              SecurityContextHolder.getContext().setAuthentication(
                  new UsernamePasswordAuthenticationToken("book-content-service", null,
                      List.of(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"))));
            }
            if (oidc.legacyStaticEnabled()
                && props.enabled()
                && auth != null
                && auth.startsWith("Bearer ")
                && MessageDigest.isEqual(
                    auth.substring(7).getBytes(StandardCharsets.UTF_8),
                    props.adminToken().getBytes(StandardCharsets.UTF_8))) {
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken(
                          "asset-admin",
                          null,
                          List.of(new SimpleGrantedAuthority("ROLE_ASSET_ADMIN"))));
            }
            String presentedServiceToken =
                serviceToken != null
                    ? serviceToken
                    : (auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null);
            if (oidc.legacyStaticEnabled()
                && presentedServiceToken != null
                && MessageDigest.isEqual(
                    presentedServiceToken.getBytes(StandardCharsets.UTF_8),
                    canonical.token().getBytes(StandardCharsets.UTF_8))) {
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken(
                          "catalog-service",
                          null,
                          List.of(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"))));
            }
            chain.doFilter(request, response);
          }
        };
    // Only explicit bearer headers authenticate; no cookie/session/Basic authentication.
    http.csrf(c -> c.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/admin/**")
                    .hasAnyAuthority(
                        "ROLE_ASSET_ADMIN",
                        "SCOPE_bookrush.catalog.read",
                        "SCOPE_bookrush.assets.write")
                    .requestMatchers("/api/admin/**", "/actuator/metrics", "/actuator/metrics/**")
                    .hasAnyAuthority("ROLE_ASSET_ADMIN", "SCOPE_bookrush.assets.write")
                    .requestMatchers("/api/v1/books/*/reader-assets/text-versions/**")
                    .hasAnyAuthority("ROLE_CATALOG_SERVICE", "SCOPE_bookrush.catalog.read")
                    .requestMatchers("/api/internal/v1/catalog/**")
                    .hasAnyAuthority(
                        "ROLE_CATALOG_SERVICE",
                        "SCOPE_bookrush.catalog.write",
                        "SCOPE_bookrush.catalog.read")
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/books/*/thumbnail")
                    .hasAnyAuthority("SCOPE_bookrush.catalog.read", "ROLE_CATALOG_SERVICE")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/books",
                        "/api/v1/books/**",
                        "/api",
                        "/api/",
                        "/api/status",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/books/*/assets/*/download-url",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, error) -> {
                      log.warn(
                          "catalog authentication rejected path={} error={}",
                          req.getRequestURI(),
                          error.getMessage());
                      res.sendError(401);
                    })
                    .accessDeniedHandler(
                        (req, res, error) -> {
                          log.warn(
                              "catalog authorization denied path={} principal={} authorities={}",
                              req.getRequestURI(),
                              req.getUserPrincipal() == null
                                  ? "anonymous"
                                  : req.getUserPrincipal().getName(),
                              SecurityContextHolder.getContext().getAuthentication() == null
                                  ? List.of()
                                  : SecurityContextHolder.getContext().getAuthentication().getAuthorities());
                          res.sendError(403);
                        }));
    if (oidc.enabled()) {
      http.oauth2ResourceServer(
          oauth ->
              oauth.jwt(
                  jwt ->
                      jwt.decoder(catalogJwtDecoder(oidc))
                          .jwtAuthenticationConverter(catalogRolesConverter())));
    }
    return http.build();
  }

  // Spring Security resolves the explicit source by this conventional name
  // before it evaluates authorization rules for a browser preflight.
  @Bean(name = "corsConfigurationSource")
  CorsConfigurationSource catalogCorsConfigurationSource(CatalogCorsProperties properties) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Accept", "Content-Type", "Idempotency-Key", "X-Admin-Client"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/internal/v1/catalog/**", configuration);
    source.registerCorsConfiguration("/api/admin/**", configuration);
    return source;
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> catalogRolesConverter() {
    return jwt -> {
      var authorities = new java.util.ArrayList<GrantedAuthority>();
      Object realm = jwt.getClaims().get("realm_access");
      if (realm instanceof java.util.Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
        roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
      }
      Object roles = jwt.getClaims().get("roles");
      if (roles instanceof List<?> values) {
        values.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
      }
      Object scopeClaim = jwt.getClaims().get("scope");
      if (scopeClaim instanceof String scope) {
        for (String value : scope.split("\\s+")) {
          if (!value.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
        }
      } else if (scopeClaim instanceof java.util.Collection<?> values) {
        values.forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));
      }
      // The decoder already enforces the configured audience. Map that
      // validated technical audience to the explicit internal authority so
      // client-credentials tokens remain stable across scope claim formats.
      if (jwt.getAudience() != null && jwt.getAudience().contains("bookrush-catalog-admin")) {
        authorities.add(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"));
      }
      return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    };
  }
}
