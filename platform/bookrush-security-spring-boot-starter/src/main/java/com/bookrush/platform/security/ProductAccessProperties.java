package com.bookrush.platform.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bookrush.security.product-access")
record ProductAccessProperties(URI decisionBaseUri, URI tokenUri, URI jwkSetUri, String workloadIssuer,
                               String audience, String scope, String clientId, String clientSecret,
                               Duration connectTimeout, Duration requestTimeout, boolean allowInsecureHttp) {
  ProductAccessProperties {
    verifyUri(decisionBaseUri, allowInsecureHttp);
    verifyUri(tokenUri, allowInsecureHttp);
    verifyUri(jwkSetUri, allowInsecureHttp);
    ProductAccessRoutes.checkText(workloadIssuer);
    verifyUri(URI.create(workloadIssuer), allowInsecureHttp);
    ProductAccessRoutes.checkText(clientId);
    if (clientSecret == null || clientSecret.isBlank() || clientSecret.contains("${")) {
      throw new IllegalArgumentException("Missing S2S secret");
    }
    audience = audience == null ? "bookrush-family" : audience;
    scope = scope == null ? "product-access.evaluate" : scope;
    if (!audience.equals("bookrush-family") || !scope.equals("product-access.evaluate")) {
      throw new IllegalArgumentException("Workload audience and scope must match product access v1");
    }
    if (decisionBaseUri.getPath() != null && !decisionBaseUri.getPath().matches("/?")) {
      throw new IllegalArgumentException("Decision base URI must be an origin");
    }
    connectTimeout = timeout(connectTimeout, Duration.ofMillis(500));
    requestTimeout = timeout(requestTimeout, Duration.ofSeconds(2));
  }

  private static Duration timeout(Duration value, Duration fallback) {
    if (value == null) return fallback;
    if (value.compareTo(Duration.ofMillis(10)) < 0 || value.compareTo(Duration.ofSeconds(5)) > 0) {
      throw new IllegalArgumentException("Product access timeouts must be 10ms to 5s");
    }
    return value;
  }

  private static void verifyUri(URI uri, boolean allowInsecureHttp) {
    if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
      throw new IllegalArgumentException("Invalid product access service URI");
    }
  }

  @Override
  public String toString() {
    return "ProductAccessProperties[configured]";
  }
}
