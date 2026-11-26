package com.bookrush.catalog.asset;

import com.bookrush.platform.security.ProductAccessRoutes;
import com.bookrush.platform.security.ProductAccessRoutes.Action;
import com.bookrush.platform.security.ProductAccessRoutes.Policy;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import com.bookrush.platform.security.ProductAccessRoutes.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bookrush.security.product-access.enabled", havingValue = "true")
class CatalogProductAccess {
  @Bean
  ProductAccessRoutes productRoutes(CatalogProductOidcProperties oidc, @Value("${catalog.security.public-bibliography:false}") boolean publicBibliography,
      @Value("${bookrush.content-human-clients:bookrush-web,bookrush-mobile}") String clients) {
    String issuer = oidc.issuer();
    String audience = oidc.audience();
    var allowed = Arrays.stream(clients.split(",")).map(String::strip).filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
    var routes = ProductAccessRoutes.builder();
    var asset = policy(issuer, audience, allowed, Type.ASSET);
    var catalog = policy(issuer, audience, allowed, Type.CATALOG);
    routes.existingSecurity("DELETE", "/api/admin/books/{bookId}/assets/{assetId}");
    routes.existingSecurity("GET", "/api");
    routes.existingSecurity("GET", "/api/");
    routes.existingSecurity("GET", "/api/admin/books/approval-history");
    routes.existingSecurity("GET", "/api/admin/books/review");
    routes.existingSecurity("GET", "/api/admin/books/{bookId}/assets");
    routes.existingSecurity("GET", "/api/admin/books/{bookId}/assets/{assetId}");
    routes.existingSecurity("GET", "/api/admin/books/{bookId}/assets/{assetId}/download-url");
    routes.existingSecurity("GET", "/api/admin/books/{bookId}/assets/{assetId}/content");
    routes.existingSecurity("GET", "/api/admin/books/{bookId}/assets/{assetId}/versions");
    routes.protect("GET", "/api/books/{bookId}/assets/{assetId}/download-url", asset);
    routes.existingSecurity("GET", "/api/internal/v1/catalog/books");
    routes.existingSecurity("GET", "/api/internal/v1/catalog/books/{bookId}");
    routes.existingSecurity("GET", "/api/internal/v1/catalog/processings/text-versions/{versionId}");
    routes.existingSecurity("GET", "/api/status");
    if (publicBibliography) routes.existingSecurity("GET", "/api/v1/books");
    else routes.protect("GET", "/api/v1/books", catalog);
    if (publicBibliography) routes.existingSecurity("GET", "/api/v1/books/{bookId}");
    else routes.protect("GET", "/api/v1/books/{bookId}", catalog);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets/chapters", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets/text-versions/{versionId}/content", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets/text-versions/{versionId}/download-url", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets/{assetId}/content", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/reader-assets/{assetId}/download-url", asset);
    routes.protect("GET", "/api/v1/books/{bookId}/thumbnail", asset);
    routes.existingSecurity("POST", "/api/admin/books/{bookId}/approve");
    routes.existingSecurity("POST", "/api/admin/books/{bookId}/assets");
    routes.existingSecurity("POST", "/api/admin/books/{bookId}/assets/{assetId}/approve");
    routes.existingSecurity("POST", "/api/admin/books/{bookId}/assets/{assetId}/versions");
    routes.existingSecurity("POST", "/api/admin/books/{bookId}/revoke-approval");
    routes.existingSecurity("POST", "/api/internal/v1/catalog/commands");
    routes.existingSecurity("POST", "/api/internal/v1/catalog/processings");
    routes.existingSecurity("POST", "/api/internal/v1/catalog/processings/chapters");
    routes.existingSecurity("POST", "/api/internal/v1/catalog/subjects");
    for (String path : List.of("/actuator", "/actuator/health", "/actuator/info", "/actuator/metrics",
        "/actuator/metrics/{requiredMetricName}", "/actuator/prometheus", "/swagger-ui.html",
        "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config")) {
      routes.existingSecurity("GET", path);
    }
    for (String method : List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")) {
      routes.existingSecurity(method, "/error");
    }
    return routes.build();
  }

  private static Policy policy(String issuer, String audience, Set<String> clients, Type type) {
    return new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, type, false,
        (request, actor) -> new Resource(type, null, null));
  }
}
