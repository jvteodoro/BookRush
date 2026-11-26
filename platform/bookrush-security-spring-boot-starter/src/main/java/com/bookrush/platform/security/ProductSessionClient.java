package com.bookrush.platform.security;

import com.bookrush.platform.core.TechnicalIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class ProductSessionClient {
  public enum AccessState {
    ADULT_ELIGIBLE, MINOR_AUTHORIZED, MINOR_AWAITING_AUTHORIZATION, MINOR_FAMILY_INACTIVE,
    AGE_PROFILE_INCOMPLETE, SUSPENDED
  }
  public enum Capability { SESSION_VIEW, PRODUCT_USE, INVITE_SELF }
  public enum Reason { ACCOUNT_RESTRICTED, ACCOUNT_SUSPENDED, SUPERVISION_REQUIRED }
  public record Snapshot(String username, AccessState accessState, Reason reasonCode,
                         Set<Capability> capabilities, Instant validUntil) {}

  private final Clock clock = Clock.systemUTC();
  private final ProductDecisionClient transport;

  public ProductSessionClient(URI origin, URI token, URI jwks, String issuer, String clientId, String secret,
      boolean allowHttp, MeterRegistry metrics) throws MalformedURLException {
    transport = new ProductDecisionClient(new ProductAccessProperties(origin, token, jwks, issuer,
        null, null, clientId, secret, null, java.time.Duration.ofSeconds(5), allowHttp), clock, metrics);
  }

  public Snapshot read(Jwt jwt, String correlation) {
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getIssuer() == null
        || jwt.getExpiresAt() == null || !clock.instant().isBefore(jwt.getExpiresAt())) throw error(401);
    var scopes = ProductAccessAuthorization.scopes(jwt.getClaims().get("scope"));
    var client = jwt.getClaimAsString("azp");
    if (client == null || !Set.of("bookrush-web", "bookrush-mobile").contains(client)
        || jwt.getAudience() == null || !jwt.getAudience().contains("bookrush-reader-bff")
        || !scopes.contains("session.view")) throw error(403);
    boolean invite = scopes.contains("family.invites") && jwt.getAudience().contains("bookrush-family");
    try {
      var body = transport.session(new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject()), invite, correlation);
      var state = AccessState.valueOf(body.path("accessState").textValue());
      var rawReason = body.path("reasonCode");
      Reason reason = rawReason.isNull() || rawReason.isMissingNode() ? null : Reason.valueOf(rawReason.textValue());
      var list = body.path("capabilities");
      var capabilities = EnumSet.noneOf(Capability.class);
      if (!list.isArray()) throw error(503);
      for (var item : list) if (!capabilities.add(Capability.valueOf(item.textValue()))) throw error(503);
      boolean product = state == AccessState.ADULT_ELIGIBLE || state == AccessState.MINOR_AUTHORIZED;
      if (capabilities.contains(Capability.PRODUCT_USE) != product || (reason == null) != product
          || capabilities.contains(Capability.INVITE_SELF) && !invite
          || (state == AccessState.SUSPENDED ? !capabilities.isEmpty() : !capabilities.contains(Capability.SESSION_VIEW))) {
        throw error(503);
      }
      var until = Instant.parse(body.path("validUntil").textValue());
      var now = clock.instant();
      if (!now.isBefore(until) || until.isAfter(now.plusSeconds(65))) throw error(503);
      if (jwt.getExpiresAt().isBefore(until)) until = jwt.getExpiresAt();
      if (!now.isBefore(until)) throw error(401);
      String username = jwt.getClaimAsString("preferred_username");
      return new Snapshot(username == null || username.isBlank() ? "Leitor" : username, state, reason,
          Set.copyOf(capabilities), until);
    } catch (ProductDecisionClient.Unavailable | IllegalArgumentException | NullPointerException
             | java.time.DateTimeException failure) {
      throw error(503);
    }
  }

  private static ResponseStatusException error(int status) {
    return new ResponseStatusException(HttpStatus.valueOf(status));
  }
}
