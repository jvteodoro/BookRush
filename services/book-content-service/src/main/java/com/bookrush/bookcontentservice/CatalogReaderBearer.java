package com.bookrush.bookcontentservice;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class CatalogReaderBearer implements ClientHttpRequestInterceptor {
  private final boolean enabled;

  CatalogReaderBearer(@Value("${bookrush.security.product-access.enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution next) throws IOException {
    if (enabled) {
      if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken identity)
          || !identity.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }
      request.getHeaders().setBearerAuth(identity.getToken().getTokenValue());
    }
    return next.execute(request, body);
  }
}
