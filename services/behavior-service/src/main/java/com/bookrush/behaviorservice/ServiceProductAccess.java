package com.bookrush.behaviorservice;

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
      @Value("${bookrush.security.issuer}") String issuer,
      @Value("${bookrush.security.audience}") String audience,
      @Value("${bookrush.product-human-clients}") String clientList) {
    if (!jwtEnabled) throw new IllegalStateException("Product access requires JWT authentication");
    var clients = Arrays.stream(clientList.split(",")).map(String::strip).filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
    var own = new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, Type.TELEMETRY,
        true, (request, actor) -> new Resource(Type.TELEMETRY, null, actor));
    var routes = ProductAccessRoutes.builder();
    routes.existingSecurity("GET", "/api/internal/v1/publisher/metrics/books/{bookId}");
    routes.existingSecurity("GET", "/api/internal/v1/publisher/metrics/books/{bookId}/summary");
    routes.existingSecurity("GET", "/api/internal/v1/recommendation/trending");
    routes.existingSecurity("GET", "/api/v1/behavior/aggregates");
    routes.existingSecurity("GET", "/api/v1/behavior/aggregates/books/{bookId}");
    routes.existingSecurity("GET", "/api/v1/behavior/aggregates/dashboard");
    routes.protect("GET", "/api/v1/behavior/aggregates/me", own);
    routes.existingSecurity("GET", "/api/v1/behavior/events/count");
    routes.existingSecurity("GET", "/api/v1/behavior/operations/metrics");
    routes.existingSecurity("POST", "/api/internal/v1/recommendation/signals");
    routes.existingSecurity("POST", "/api/v1/behavior/aggregates/rebuild");
    routes.protect("POST", "/api/v1/behavior/events", own);
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
