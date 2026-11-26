package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "TEST_KEYCLOAK_ORIGIN", matches = ".+")
class PublisherKeycloakTest {
  record Token(String access_token) {}

  @Test
  void validatesRealTokensAndKeepsEqualSubjectsInDifferentRoles() {
    String origin = System.getenv("TEST_KEYCLOAK_ORIGIN");
    String product = origin + "/realms/bookrush";
    String editorial = System.getenv("TEST_KEYCLOAK_PLATFORM_ORIGIN") + "/realms/bookrush-platform";
    var identities = new PublisherIdentity(product, editorial, "bookrush-web");
    var author = authenticate(product, "bookrush-web");
    var operator = authenticate(editorial, "bookrush-editorial");
    assertEquals(author.getToken().getSubject(), operator.getToken().getSubject());
    assertEquals(author.getToken().getClaimAsString("email"), operator.getToken().getClaimAsString("email"));
    assertEquals(author.getToken().getClaimAsString("preferred_username"), operator.getToken().getClaimAsString("preferred_username"));
    assertNotEquals(identities.author(author), identities.operator(operator));
    assertEquals(403, assertThrows(ResponseStatusException.class, () -> identities.author(operator)).getStatusCode().value());
    assertEquals(403, assertThrows(ResponseStatusException.class, () -> identities.operator(author)).getStatusCode().value());
  }

  private static JwtAuthenticationToken authenticate(String issuer, String client) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", client);
    form.add("username", "shared-name");
    form.add("password", "fixture-only");
    form.add("scope", "openid profile email");
    var token = RestClient.create().post().uri(issuer + "/protocol/openid-connect/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Token.class);
    assertNotNull(token);
    var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    var jwt = decoder.decode(token.access_token());
    var groups = jwt.getClaimAsStringList("groups");
    var roles = groups == null ? List.<SimpleGrantedAuthority>of()
        : groups.stream().map(group -> new SimpleGrantedAuthority("ROLE_" + group)).toList();
    return new JwtAuthenticationToken(jwt, roles);
  }
}
