package com.bookrush.analytics.api;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AnalyticsSecurityConfiguration {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      @Value("${bookrush.security.enabled:false}") boolean enabled,
      @Value("${bookrush.security.issuer:}") String issuer,
      @Value("${bookrush.security.audience:bookrush-analytics}") String audience)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      "/actuator/health/**",
                      "/v3/api-docs/**",
                      "/swagger-ui/**",
                      "/swagger-ui.html")
                  .permitAll();
              if (enabled) {
                auth.requestMatchers("/api/admin/v1/annotation/campaigns/**")
                    .hasAnyRole("ANALYTICS_OPERATOR", "EXCERPT_REVIEWER");
                auth.requestMatchers("/api/admin/v1/annotation/items/*/adjudicate")
                    .hasAnyRole("ANALYTICS_OPERATOR", "EXCERPT_REVIEWER");
                auth.requestMatchers("/api/admin/v1/annotation/items/**")
                    .hasAnyRole("EXCERPT_ANNOTATOR", "ANALYTICS_OPERATOR");
                auth.requestMatchers("/api/admin/**", "/api/internal/**")
                    .hasAuthority("SCOPE_bookrush.analytics")
                    .anyRequest()
                    .authenticated();
              } else auth.anyRequest().permitAll();
            });
    if (enabled) {
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
      OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuer);
      OAuth2TokenValidator<Jwt> audienceValidator =
          token ->
              token.getAudience().contains(audience)
                  ? OAuth2TokenValidatorResult.success()
                  : OAuth2TokenValidatorResult.failure(
                      new OAuth2Error("invalid_token", "required audience is missing", null));
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
      http.oauth2ResourceServer(
          oauth ->
              oauth.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(groupsConverter())));
    }
    return http.build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> groupsConverter() {
    return jwt -> {
      List<GrantedAuthority> authorities = new ArrayList<>();
      Object scope = jwt.getClaims().get("scope");
      if (scope instanceof String values)
        for (String value : values.split("\\s+"))
          if (!value.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
      Object groups = jwt.getClaims().get("groups");
      if (groups instanceof List<?> values)
        for (Object group : values) {
          String g = String.valueOf(group);
          if ("operators".equals(g) || "platform-admins".equals(g))
            authorities.add(new SimpleGrantedAuthority("ROLE_ANALYTICS_OPERATOR"));
          if ("excerpt-annotators".equals(g) || "developers".equals(g))
            authorities.add(new SimpleGrantedAuthority("ROLE_EXCERPT_ANNOTATOR"));
          if ("excerpt-reviewers".equals(g) || "platform-admins".equals(g))
            authorities.add(new SimpleGrantedAuthority("ROLE_EXCERPT_REVIEWER"));
        }
      String principal = jwt.getSubject();
      if (principal == null || principal.isBlank()) {
        principal = "session:" + String.valueOf(jwt.getClaims().getOrDefault("sid", "unknown"));
      }
      return new JwtAuthenticationToken(jwt, authorities, principal);
    };
  }
}
