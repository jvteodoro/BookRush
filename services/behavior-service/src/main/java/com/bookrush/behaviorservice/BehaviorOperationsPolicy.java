package com.bookrush.behaviorservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "bookrush.security.product-access.enabled", havingValue = "true")
public final class BehaviorOperationsPolicy {
  public static final String READ = "bookrush.behavior.operations.read";
  public static final String REBUILD = "bookrush.behavior.rebuild";

  private final String issuer;
  private final String audience;
  private final String client;

  BehaviorOperationsPolicy(@Value("${bookrush.security.s2s-issuer}") String issuer,
      @Value("${bookrush.security.s2s-audience}") String audience,
      @Value("${bookrush.security.operations-client}") String client) {
    if (issuer.isBlank() || audience.isBlank() || client.isBlank()) {
      throw new IllegalStateException("Behavior operations require a platform issuer, audience and client");
    }
    this.issuer = issuer;
    this.audience = audience;
    this.client = client;
  }

  boolean allows(Authentication authentication, String scope) {
    return authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()
        && issuer.equals(jwt.getToken().getClaimAsString("iss"))
        && jwt.getToken().getAudience() != null && jwt.getToken().getAudience().contains(audience)
        && client.equals(jwt.getToken().getClaimAsString("azp"))
        && jwt.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("SCOPE_" + scope));
  }

  public void require(String scope) {
    if (!allows(SecurityContextHolder.getContext().getAuthentication(), scope)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "behavior operations workload required");
    }
  }
}
