package com.bookrush.ingestion.catalog;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion.catalog")
public record CanonicalCatalogProperties(@NotNull URI baseUrl, String serviceToken,
    String assetToken, @NotNull Duration requestTimeout, boolean oidcEnabled,
    boolean legacyStaticEnabled, URI oidcTokenUri,
    String canonicalClientId, String canonicalClientSecret, String canonicalScope,
    String assetClientId, String assetClientSecret, String assetScope) {
  public CanonicalCatalogProperties {
    serviceToken = serviceToken == null ? "" : serviceToken;
    assetToken = assetToken == null ? "" : assetToken;
    canonicalScope = canonicalScope == null ? "bookrush.catalog.write" : canonicalScope;
    assetScope = assetScope == null ? "bookrush.assets.write" : assetScope;
  }
}
