package com.bookrush.adminservice;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminSecurityConfigurationTest {
  @Test
  void mapsCanonicalPlatformRealmRolesAndGroups() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("operator-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .claim("scope", "openid")
        .claim("groups", List.of("platform-admins"))
        .claim("realm_access", Map.of("roles", List.of("platform-admin", "platform-operator")))
        .build();

    var authentication = new AdminSecurityConfiguration().groupsConverter().convert(jwt);
    var authorities = authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority()).toList();

    assertTrue(authorities.contains("ROLE_platform-admins"));
    assertTrue(authorities.contains("ROLE_platform-admin"));
    assertTrue(authorities.contains("ROLE_platform-operator"));
  }
}
