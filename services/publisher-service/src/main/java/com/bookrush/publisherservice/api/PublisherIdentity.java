package com.bookrush.publisherservice.api;

import com.bookrush.platform.core.TechnicalIdentity;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class PublisherIdentity {
  private final String productIssuer;
  private final String editorialIssuer;
  private final Set<String> authorClients;

  PublisherIdentity(@Value("${publisher.identity.product-issuer}") String productIssuer,
      @Value("${publisher.identity.editorial-issuer}") String editorialIssuer,
      @Value("${publisher.identity.author-clients}") String authorClients) {
    if (productIssuer.isBlank() || editorialIssuer.isBlank() || productIssuer.equals(editorialIssuer)) {
      throw new IllegalArgumentException("Publisher requires distinct product and editorial issuers");
    }
    this.productIssuer = productIssuer;
    this.editorialIssuer = editorialIssuer;
    this.authorClients = Set.copyOf(Arrays.stream(authorClients.split(",")).map(String::strip)
        .filter(client -> !client.isBlank()).toList());
  }

  TechnicalIdentity author(Principal principal) {
    var token = authenticated(principal);
    var identity = identity(token);
    var client = token.getToken().getClaimAsString("azp");
    if (!productIssuer.equals(identity.issuer()) || client == null || !authorClients.contains(client)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "product author required");
    }
    return identity;
  }

  TechnicalIdentity operator(Principal principal) {
    var token = authenticated(principal);
    var identity = identity(token);
    if (!editorialIssuer.equals(identity.issuer()) || token.getAuthorities().stream().noneMatch(
        authority -> Set.of("ROLE_platform-admins", "ROLE_operators").contains(authority.getAuthority()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "editorial operator required");
    }
    return identity;
  }

  private static JwtAuthenticationToken authenticated(Principal principal) {
    if (!(principal instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "verified JWT required");
    }
    return token;
  }

  private static TechnicalIdentity identity(JwtAuthenticationToken token) {
    var jwt = token.getToken();
    if (jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "technical identity required");
    }
    return new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject());
  }
}
