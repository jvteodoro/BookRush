package com.bookrush.readerprofileservice;

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
    var own = new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, Type.PROFILE,
        true, (request, actor) -> new Resource(Type.PROFILE, null, actor));
    var general = new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, Type.PROFILE,
        false, (request, actor) -> new Resource(Type.PROFILE, null, null));
    var routes = ProductAccessRoutes.builder();
    routes.protect("GET", "/api/v1/profile", own);
    routes.protect("GET", "/api/v1/profile/privacy", own);
    routes.protect("GET", "/api/v1/profile/public", general);
    routes.protect("GET", "/api/v1/profile/search", general);
    routes.protect("PUT", "/api/v1/profile", own);
    routes.protect("PUT", "/api/v1/profile/privacy", own);
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
