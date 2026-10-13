package com.bookrush.catalog.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.security.product-oidc")
public record CatalogProductOidcProperties(
    boolean enabled, String issuer, String jwkSetUri, String audience) {
  public CatalogProductOidcProperties {
    issuer = issuer == null ? "" : issuer;
    jwkSetUri = jwkSetUri == null ? "" : jwkSetUri;
    audience = audience == null ? "" : audience;
  }
}
