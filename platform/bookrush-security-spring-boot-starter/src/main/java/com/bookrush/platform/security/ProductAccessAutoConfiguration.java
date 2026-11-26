package com.bookrush.platform.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.time.Clock;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.handler.MappedInterceptor;

@AutoConfiguration
@ConditionalOnProperty(prefix = "bookrush.security.product-access", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ProductAccessProperties.class)
public class ProductAccessAutoConfiguration {
  @Bean
  ProductAccessAuthorization productAccessAuthorization(ProductAccessProperties config, ProductAccessRoutes routes,
      ObjectProvider<MeterRegistry> metrics) throws MalformedURLException {
    Clock clock = Clock.systemUTC();
    return new ProductAccessAuthorization(routes, new ProductDecisionClient(config, clock, metrics.getIfUnique()), clock);
  }

  @Bean
  MappedInterceptor productAccessMvcInterceptor(ProductAccessAuthorization authorization) {
    return new MappedInterceptor(null, authorization);
  }

  @Bean
  HandlerExceptionResolver productAccessFailureResolver(ProductAccessAuthorization authorization) {
    var resolver = new AbstractHandlerExceptionResolver() {
      @Override
      protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
          Object handler, Exception failure) {
        if (!(failure instanceof ProductAccessAuthorization.Failure denied)) return null;
        try {
          authorization.writeProblem(request, response, denied);
          return new ModelAndView();
        } catch (java.io.IOException disconnected) {
          return null;
        }
      }
    };
    resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return resolver;
  }

  @Bean
  SmartInitializingSingleton productAccessMvcRequired(ObjectProvider<RequestMappingHandlerMapping> mappings) {
    return () -> {
      if (mappings.stream().findAny().isEmpty()) throw new IllegalStateException("Product access requires Spring MVC");
    };
  }
}
