package com.bookrush.bookcontentservice;

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
class ContentProductAccess {
  @Bean
  ProductAccessRoutes productRoutes(@Value("${bookrush.security.enabled:false}") boolean jwtEnabled,
      @Value("${bookrush.security.issuer}") String issuer, @Value("${bookrush.security.audience}") String audience,
      @Value("${bookrush.content-human-clients:bookrush-web,bookrush-mobile}") String clients) {
    if (!jwtEnabled) throw new IllegalStateException("Content authorization requires JWT");
    var allowed = Arrays.stream(clients.split(",")).map(String::strip).filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
    var routes = ProductAccessRoutes.builder();
    var asset = policy(issuer, audience, allowed, Type.ASSET);
    var book = policy(issuer, audience, allowed, Type.BOOK);
    routes.protect("GET", "/api/v1/content/books/{bookId}", book);
    routes.protect("GET", "/api/v1/content/books/{bookId}/assets", asset);
    routes.protect("GET", "/api/v1/content/books/{bookId}/assets/{assetId}/download-url", asset);
    routes.protect("GET", "/api/v1/content/books/{bookId}/chapters", book);
    routes.protect("GET", "/api/v1/content/books/{bookId}/chapters/{chapterId}/content", book);
    routes.protect("GET", "/api/v1/content/books/{bookId}/publication.json", book);
    routes.protect("GET", "/api/v1/content/books/{bookId}/resources/{*resource}", asset);
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

  @Bean(name = "corsConfigurationSource")
  org.springframework.web.cors.CorsConfigurationSource contentCorsConfigurationSource(
      @Value("${BOOKRUSH_CORS_ALLOWED_ORIGINS:https://bookrush.jteodoro.tec.br,http://localhost:18080}") String origins) {
    var cors = new org.springframework.web.cors.CorsConfiguration();
    var allowed = Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList();
    if (allowed.contains("*")) throw new IllegalArgumentException("Content CORS requires explicit origins");
    cors.setAllowedOrigins(allowed);
    cors.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Accept", "Range", "X-Correlation-ID"));
    var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/content/**", cors);
    return source;
  }

  private static Policy policy(String issuer, String audience, Set<String> clients, Type type) {
    return new Policy(issuer, audience, clients, Set.of(), Action.PRODUCT_USE, type, false,
        (request, actor) -> new Resource(type, null, null));
  }
}
