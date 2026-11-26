package com.bookrush.readerstateservice;

import com.bookrush.platform.security.ProductAccessRoutes;
import com.bookrush.platform.security.ProductAccessRoutes.Policy;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static com.bookrush.platform.security.ProductAccessRoutes.Action.PRODUCT_USE;
import static com.bookrush.platform.security.ProductAccessRoutes.Type.READER_STATE;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bookrush.security.product-access.enabled", havingValue = "true")
class ReaderStateProductAccess {
  @Bean
  ProductAccessRoutes readerStateProductRoutes(
      @Value("${bookrush.security.enabled:false}") boolean jwtEnabled,
      @Value("${bookrush.security.issuer}") String issuer,
      @Value("${bookrush.security.audience}") String audience,
      @Value("${reader-state.product-access.human-clients}") String clients) {
    if (!jwtEnabled) throw new IllegalStateException("Reader state product access requires JWT authentication");
    var allowedClients = Arrays.stream(clients.split(",")).map(String::strip).filter(value -> !value.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
    var policy = new Policy(issuer, audience, allowedClients, Set.of(), PRODUCT_USE, READER_STATE, true,
        (request, actor) -> new Resource(READER_STATE, null, actor));

    var routes = ProductAccessRoutes.builder()
        .existingSecurity("GET", "/api/internal/v1/reader/supervision/{memberId}/history")
        .protect("DELETE", "/api/v1/reader/books/{bookId}/bookmarks/{bookmarkId}", policy)
        .protect("DELETE", "/api/v1/reader/library/{bookId}", policy)
        .protect("GET", "/api/v1/reader/books/seen", policy)
        .protect("GET", "/api/v1/reader/books/{bookId}/bookmarks", policy)
        .protect("GET", "/api/v1/reader/books/{bookId}/progress", policy)
        .protect("GET", "/api/v1/reader/library", policy)
        .protect("GET", "/api/v1/reader/plant", policy)
        .protect("GET", "/api/v1/reader/recent", policy)
        .protect("GET", "/api/v1/reader/streak", policy)
        .protect("POST", "/api/v1/reader/books/{bookId}/bookmarks", policy)
        .protect("POST", "/api/v1/reader/books/{bookId}/opened", policy)
        .protect("POST", "/api/v1/reader/plant", policy)
        .protect("POST", "/api/v1/reader/sessions", policy)
        .protect("POST", "/api/v1/reader/sessions/{id}/end", policy)
        .protect("PUT", "/api/v1/reader/books/{bookId}/bookmarks/{bookmarkId}", policy)
        .protect("PUT", "/api/v1/reader/books/{bookId}/progress", policy)
        .protect("PUT", "/api/v1/reader/library/{bookId}", policy);
    for (String path : List.of("/actuator", "/actuator/health",
        "/actuator/info", "/actuator/metrics", "/actuator/metrics/{requiredMetricName}",
        "/actuator/prometheus", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs.yaml",
        "/v3/api-docs/swagger-config")) {
      routes.existingSecurity("GET", path);
    }
    for (String method : List.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")) {
      routes.existingSecurity(method, "/error");
    }
    return routes.build();
  }
}
