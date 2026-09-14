package com.bookrush.platform.security;

import com.bookrush.platform.core.TechnicalIdentity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@ConditionalOnProperty(prefix = "bookrush.security", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BookrushSecurityProperties.class)
public class BookrushSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  JwtDecoder bookrushJwtDecoder(BookrushSecurityProperties properties) {
    require(properties.getIssuer(), "bookrush.security.issuer");
    require(properties.getJwkSetUri(), "bookrush.security.jwk-set-uri");
    require(properties.getAudience(), "bookrush.security.audience");
    if (properties.getClockSkew() == null || properties.getClockSkew().isNegative()) {
      throw new IllegalStateException("bookrush.security.clock-skew must be non-negative");
    }
    var decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    OAuth2TokenValidator<Jwt> audience = token -> token.getAudience() != null
        && token.getAudience().contains(properties.getAudience())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience mismatch", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.getIssuer()), audience));
    return decoder;
  }

  /** Converts only claims from the already validated JWT; it never chooses an issuer or JWKS URL. */
  @Bean
  @ConditionalOnMissingBean(name = "bookrushJwtAuthenticationConverter")
  BookrushJwtAuthenticationConverter bookrushJwtAuthenticationConverter() {
    return new BookrushJwtAuthenticationConverter();
  }

  /**
   * A concrete, parameterized converter prevents Spring MVC from treating the
   * security converter as an untyped formatting converter during startup.
   */
  static final class BookrushJwtAuthenticationConverter
      implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
      List<GrantedAuthority> authorities = new ArrayList<>();
      Object realmAccess = jwt.getClaims().get("realm_access");
      if (realmAccess instanceof java.util.Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
      }
      Object scopes = jwt.getClaims().get("scope");
      if (scopes instanceof String value) {
        for (String scope : value.split(" ")) {
          if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
      }
      var identity = new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
      return new JwtAuthenticationToken(jwt, authorities, identity.toString());
    }
  }

  private static void require(String value, String property) {
    if (value == null || value.isBlank()) throw new IllegalStateException(property + " is required when security is enabled");
  }
}
