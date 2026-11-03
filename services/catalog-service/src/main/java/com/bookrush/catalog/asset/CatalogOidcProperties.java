package com.bookrush.catalog.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.security.oidc")
public record CatalogOidcProperties(
    boolean enabled,
    String issuer,
    String jwkSetUri,
    String audience,
    boolean legacyStaticEnabled,
    String readerInternalToken) {
  public CatalogOidcProperties {
    issuer = issuer == null ? "" : issuer;
    jwkSetUri = jwkSetUri == null ? "" : jwkSetUri;
    audience = audience == null ? "" : audience;
    readerInternalToken = readerInternalToken == null ? "" : readerInternalToken;
  }
}
