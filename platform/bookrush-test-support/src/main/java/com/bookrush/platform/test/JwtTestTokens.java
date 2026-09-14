package com.bookrush.platform.test;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** Explicit test-only JWT-shaped values; no signing key or production bypass. */
public final class JwtTestTokens {
  private JwtTestTokens() {}

  public static Jwt productUser(String subject) {
    return token("https://keycloak.example.test/realms/bookrush", "bookrush-web", subject, List.of("READER"));
  }

  public static Jwt platformOperator(String subject) {
    return token("https://keycloak.example.test/realms/bookrush-platform", "bookrush-ingestion-admin", subject, List.of("platform-operator"));
  }

  public static Jwt token(String issuer, String audience, String subject, List<String> roles) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return Jwt.withTokenValue("test-only-token")
        .header("alg", "RS256")
        .issuer(issuer)
        .subject(subject)
        .audience(List.of(audience))
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim("realm_access", java.util.Map.of("roles", roles))
        .build();
  }
}
