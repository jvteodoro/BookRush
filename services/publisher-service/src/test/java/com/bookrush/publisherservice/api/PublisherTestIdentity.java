package com.bookrush.publisherservice.api;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class PublisherTestIdentity {
  static final String PRODUCT = "https://identity.fixture/realms/bookrush";
  static final String EDITORIAL = "https://identity.fixture/realms/bookrush-platform";

  static PublisherIdentity identities() { return new PublisherIdentity(PRODUCT, EDITORIAL, "bookrush-web,bookrush-mobile"); }

  static JwtAuthenticationToken author(String subject) {
    return token(PRODUCT, subject, "bookrush-web", List.of());
  }

  static JwtAuthenticationToken operator(String subject) {
    return token(EDITORIAL, subject, "bookrush-editorial", List.of(new SimpleGrantedAuthority("ROLE_operators")));
  }

  private static JwtAuthenticationToken token(String issuer, String subject, String client,
      List<SimpleGrantedAuthority> roles) {
    var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").issuer(issuer).subject(subject)
        .claim("azp", client).claim("preferred_username", "same-name").claim("email", "same@example.invalid").build();
    return new JwtAuthenticationToken(jwt, roles);
  }

  private PublisherTestIdentity() {}
}
