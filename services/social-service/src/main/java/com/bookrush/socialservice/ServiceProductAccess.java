package com.bookrush.socialservice;

import com.bookrush.platform.security.ProductAccessRoutes;
import com.bookrush.platform.security.ProductAccessRoutes.*;
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
class ServiceProductAccess {
  @Bean
  ProductAccessRoutes productRoutes(
      @Value("${bookrush.security.enabled:false}") boolean jwtEnabled,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${bookrush.security.audience}") String audience,
      @Value("${bookrush.product-human-clients}") String clientList) {
    if (!jwtEnabled) throw new IllegalStateException("Product access requires JWT authentication");
    var clients = Arrays.stream(clientList.split(",")).map(String::strip).filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
    var general = new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, Type.COMMENT,
        false, (request, actor) -> new Resource(Type.COMMENT, null, null));
    var routes = ProductAccessRoutes.builder();
    routes.existingSecurity("GET", "/api/internal/v1/social/supervision/{memberId}/comments");
    routes.protect("DELETE", "/api/v1/social/books/{bookId}/like", general);
    routes.protect("DELETE", "/api/v1/social/books/{bookId}/ratings", general);
    routes.protect("DELETE", "/api/v1/social/comments/{commentId}", general);
    routes.protect("DELETE", "/api/v1/social/follow", general);
    routes.protect("GET", "/api/v1/social/books/{bookId}/comments", general);
    routes.protect("GET", "/api/v1/social/books/{bookId}/likes", general);
    routes.protect("GET", "/api/v1/social/books/{bookId}/ratings", general);
    routes.protect("GET", "/api/v1/social/follow", general);
    routes.protect("GET", "/api/v1/social/followers", general);
    routes.existingSecurity("GET", "/api/v1/social/moderation/reports");
    routes.protect("POST", "/api/v1/social/books/{bookId}/comments", general);
    routes.protect("POST", "/api/v1/social/books/{bookId}/share", general);
    routes.protect("POST", "/api/v1/social/reports", general);
    routes.protect("PUT", "/api/v1/social/books/{bookId}/like", general);
    routes.protect("PUT", "/api/v1/social/books/{bookId}/ratings", general);
    routes.protect("PUT", "/api/v1/social/follow", general);
    routes.existingSecurity("PUT", "/api/v1/social/moderation/comments/{commentId}");
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
}
