package com.bookrush.family;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes;
import com.bookrush.platform.security.ProductAccessRoutes.Policy;
import com.bookrush.platform.security.ProductAccessRoutes.Resource;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import static com.bookrush.platform.security.ProductAccessRoutes.Action.*;
import static com.bookrush.platform.security.ProductAccessRoutes.Type.*;

/** Disposable real starter consumer launched by the Premium/Family E2E. */
@Configuration(proxyBeanMethods = false)
@Profile("canonical-consumer-fixture")
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class CanonicalConsumer {
  public static void main(String[] args) {
    new SpringApplicationBuilder(CanonicalConsumer.class).profiles("canonical-consumer-fixture").run(args);
  }

  @Bean
  ProductAccessRoutes routes(@Value("${bookrush.security.issuer}") String issuer,
      @Value("${fixture.minor-subject}") String minor) {
    var clients = Set.of("fixture-product");
    return ProductAccessRoutes.builder()
        .protect("GET", "/read", new Policy(issuer, "bookrush-family", clients, Set.of(),
            PRODUCT_USE, BOOK, false, (request, actor) -> new Resource(BOOK, "fixture", null)))
        .protect("GET", "/session", new Policy(issuer, "bookrush-family", clients, Set.of(),
            SESSION_VIEW, SESSION, true, (request, actor) -> new Resource(SESSION, null, actor)))
        .protect("GET", "/comments/{id}", new Policy(issuer, "bookrush-family", clients, Set.of(),
            SUPERVISE_COMMENTS, COMMENT, true, (request, actor) -> {
              var route = (java.util.Map<?, ?>) request.getAttribute(
                  org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
              if (!minor.equals(route.get("id"))) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
              }
              return new Resource(COMMENT, null, new TechnicalIdentity(issuer, minor));
            }))
        .existingSecurity("GET", "/actuator/health")
        .build();
  }

  @RestController
  @Profile("canonical-consumer-fixture")
  static class Endpoints {
    @GetMapping("/read") String read() { return "fixture book"; }
    @GetMapping("/session") String session() { return "fixture session"; }
    @GetMapping("/comments/{id}") String comments(@PathVariable("id") String id) { return "fixture comments"; }
  }
}
