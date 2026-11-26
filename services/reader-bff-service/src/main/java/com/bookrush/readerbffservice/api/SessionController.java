package com.bookrush.readerbffservice.api;

import com.bookrush.platform.security.ProductAccessAuthorization;
import com.bookrush.platform.security.ProductSessionClient;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "bookrush.session.enabled", havingValue = "true")
class SessionController {
  private final ProductSessionClient sessions;

  SessionController(@Value("${bookrush.session.family-url}") URI family,
      @Value("${bookrush.session.token-url}") URI token, @Value("${bookrush.session.jwks}") URI jwks,
      @Value("${bookrush.session.workload-issuer}") String issuer, @Value("${bookrush.session.secret}") String secret,
      @Value("${bookrush.session.allow-insecure-http}") boolean allowHttp,
      @Value("${bookrush.security.enabled}") boolean securityEnabled, MeterRegistry metrics) throws MalformedURLException {
    if (!securityEnabled) throw new IllegalStateException("Session bootstrap requires verified product JWTs");
    sessions = new ProductSessionClient(family, token, jwks, issuer, "bookrush-reader-bff-session-s2s", secret,
        allowHttp, metrics);
  }

  @GetMapping("/api/v1/session")
  ResponseEntity<ProductSessionClient.Snapshot> session(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
    String correlation = ProductAccessAuthorization.supervisionCorrelation(request);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Correlation-ID", correlation)
        .body(sessions.read(jwt, correlation));
  }
}
