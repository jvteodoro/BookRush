package com.bookrush.ingestion.catalog;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Obtains short-lived client-credentials tokens; secrets never leave this process. */
@Component
public final class OidcServiceTokenProvider {
  public enum Kind { CANONICAL, ASSET }
  private final RestClient client;
  private final CanonicalCatalogProperties properties;
  private final Map<Kind, Cached> cache = new ConcurrentHashMap<>();

  public OidcServiceTokenProvider(RestClient.Builder builder, CanonicalCatalogProperties properties) {
    this.client = builder.build();
    this.properties = properties;
  }

  public String token(Kind kind) {
    if (!properties.oidcEnabled()) return legacy(kind);
    if (properties.oidcTokenUri() == null) throw new IllegalStateException("OIDC token URI is required");
    Cached current = cache.get(kind);
    if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) return current.value();
    synchronized (cache) {
      current = cache.get(kind);
      if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) return current.value();
      String clientId = kind == Kind.CANONICAL ? properties.canonicalClientId() : properties.assetClientId();
      String secret = kind == Kind.CANONICAL ? properties.canonicalClientSecret() : properties.assetClientSecret();
      String scope = kind == Kind.CANONICAL ? properties.canonicalScope() : properties.assetScope();
      if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
        throw new IllegalStateException("OIDC service credentials are missing for " + kind);
      }
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "client_credentials"); form.add("client_id", clientId);
      form.add("client_secret", secret); form.add("scope", scope);
      Map<?, ?> response = client.post().uri(properties.oidcTokenUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().body(Map.class);
      if (response == null || response.get("access_token") == null) throw new IllegalStateException("OIDC token response did not contain access_token");
      long expires = response.get("expires_in") instanceof Number n ? n.longValue() : 300;
      Cached next = new Cached(String.valueOf(response.get("access_token")), Instant.now().plusSeconds(Math.max(30, expires)));
      cache.put(kind, next);
      return next.value();
    }
  }

  private String legacy(Kind kind) {
    if (!properties.legacyStaticEnabled()) {
      throw new IllegalStateException("No service credential mode is enabled for " + kind);
    }
    String token = kind == Kind.CANONICAL ? properties.serviceToken() : properties.assetToken();
    if (token.isBlank()) throw new IllegalStateException("Legacy service credential is empty for " + kind);
    return token;
  }
  private record Cached(String value, Instant expiresAt) {}
}
