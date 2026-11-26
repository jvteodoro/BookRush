package com.bookrush.readerbffservice.api;

import com.bookrush.platform.security.ProductAccessAuthorization;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "bookrush.supervision.enabled", havingValue = "true")
class SupervisedReadingController {
  private static final String HISTORY_SCOPE = "supervision.history.read";
  private static final String CLIENT = "bookrush-reader-bff-supervision-s2s";
  enum Scope { BOOKS_ACCESSED, READING_ACTIVITY, COMMENTS }
  record Book(UUID bookId, java.time.Instant accessedAt) {}
  record Day(java.time.LocalDate date, long minutes, long sessions) {}
  record Comment(UUID id, UUID bookId, String body, java.time.Instant createdAt) {}
  record Page(Scope scope, List<?> items, UUID nextCursor) {}
  private final RestClient state;
  private final RestClient social;
  private final RestClient keycloak;
  private final URI tokenUri;
  private final String secret;

  SupervisedReadingController(@Value("${bookrush.reader-state-url}") URI stateOrigin,
      @Value("${bookrush.supervision.token-uri}") URI tokenUri, @Value("${bookrush.supervision.client-secret}") String secret,
      @Value("${bookrush.security.enabled}") boolean securityEnabled,
      @Value("${bookrush.supervision.allow-insecure-http:false}") boolean allowHttp,
      @Value("${bookrush.supervision.comments-enabled:false}") boolean commentsEnabled,
      @Value("${bookrush.supervision.social-url}") URI socialOrigin) {
    if (!securityEnabled || secret.isBlank() || !valid(stateOrigin, allowHttp) || !valid(tokenUri, allowHttp)
        || commentsEnabled && !valid(socialOrigin, allowHttp)) {
      throw new IllegalStateException("Supervised reading requires authenticated internal endpoints and a workload credential");
    }
    var factory = new SimpleClientHttpRequestFactory() {
      @Override protected void prepareConnection(java.net.HttpURLConnection connection, String method)
          throws java.io.IOException {
        super.prepareConnection(connection, method);
        connection.setInstanceFollowRedirects(false);
      }
    };
    factory.setConnectTimeout(Duration.ofMillis(500));
    factory.setReadTimeout(Duration.ofSeconds(4));
    this.state = RestClient.builder().requestFactory(factory).baseUrl(stateOrigin.toString()).build();
    this.social = commentsEnabled ? RestClient.builder().requestFactory(factory).baseUrl(socialOrigin.toString()).build() : null;
    this.keycloak = RestClient.builder().requestFactory(factory).build();
    this.secret = secret;
    this.tokenUri = tokenUri;
  }

  @GetMapping("/api/v1/reader/supervision/{memberId}/history")
  ResponseEntity<Page> history(@PathVariable UUID memberId, @RequestParam Scope scope,
      @RequestParam(defaultValue = "25") int limit, @RequestParam(required = false) UUID cursor,
      JwtAuthenticationToken guardian, HttpServletRequest request) {
    if (scope == Scope.COMMENTS) throw error(400);
    return page(state, "/api/internal/v1/reader/supervision/{memberId}/history", HISTORY_SCOPE,
        scope, memberId, limit, cursor, guardian, request);
  }

  @GetMapping("/api/v1/reader/supervision/{memberId}/comments")
  ResponseEntity<Page> comments(@PathVariable UUID memberId, @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) UUID cursor, JwtAuthenticationToken guardian, HttpServletRequest request) {
    if (social == null) throw error(404);
    return page(social, "/api/internal/v1/social/supervision/{memberId}/comments", "supervision.comments.read",
        Scope.COMMENTS, memberId, limit, cursor, guardian, request);
  }

  private ResponseEntity<Page> page(RestClient owner, String path, String workloadScope, Scope scope, UUID memberId,
      int limit, UUID cursor, JwtAuthenticationToken guardian, HttpServletRequest request) {
    if (guardian == null || guardian.getToken().getSubject() == null || guardian.getToken().getSubject().isBlank()
        || guardian.getToken().getAudience() == null
        || !guardian.getToken().getAudience().contains("bookrush-reader-bff")) throw error(401);
    String scopes = guardian.getToken().getClaimAsString("scope");
    String caller = guardian.getToken().getClaimAsString("azp");
    if (scopes == null || !List.of(scopes.split(" ")).contains("supervision.read") || caller == null
        || !Set.of("bookrush-web", "bookrush-mobile").contains(caller)) throw error(403);
    if (limit < 1 || limit > 50) throw error(400);

    // The data owner resolves the target and checks the canonical decision for this projection.
    String workload = workload(workloadScope);
    try {
      var response = owner.get().uri(builder -> {
        builder.path(path).queryParam("limit", limit);
        if (scope != Scope.COMMENTS) builder.queryParam("scope", scope);
        if (cursor != null) builder.queryParam("cursor", cursor);
        return builder.build(memberId);
      }).headers(headers -> {
        headers.setBearerAuth(guardian.getToken().getTokenValue());
        headers.set("X-BookRush-Workload", workload);
        headers.set("X-Correlation-ID", ProductAccessAuthorization.supervisionCorrelation(request));
      }).retrieve().toEntity(JsonNode.class);
      JsonNode body = response.getBody();
      if (response.getStatusCode().value() != 200 || body == null || !scope.name().equals(body.path("scope").asText())
          || !body.path("items").isArray() || body.path("items").size() > limit) throw error(503);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(project(scope, body));
    } catch (HttpStatusCodeException failure) {
      int status = failure.getStatusCode().value();
      throw error(Set.of(400, 401, 403, 404, 503).contains(status) ? status : 503);
    } catch (RestClientException failure) {
      throw error(503);
    }
  }

  private static Page project(Scope scope, JsonNode body) {
    try {
      var items = new java.util.ArrayList<>();
      for (JsonNode item : body.path("items")) {
        if (scope == Scope.BOOKS_ACCESSED) {
          items.add(new Book(UUID.fromString(item.path("bookId").textValue()),
              java.time.Instant.parse(item.path("accessedAt").textValue())));
        } else if (scope == Scope.COMMENTS) {
          String text = item.path("body").textValue();
          if (text == null || text.length() > 4000) throw error(503);
          items.add(new Comment(UUID.fromString(item.path("id").textValue()),
              UUID.fromString(item.path("bookId").textValue()), text,
              java.time.Instant.parse(item.path("createdAt").textValue())));
        } else {
          long minutes = item.path("minutes").isIntegralNumber() ? item.path("minutes").asLong(-1) : -1;
          long sessions = item.path("sessions").isIntegralNumber() ? item.path("sessions").asLong(-1) : -1;
          if (minutes < 0 || sessions < 1) throw error(503);
          items.add(new Day(java.time.LocalDate.parse(item.path("date").textValue()), minutes, sessions));
        }
      }
      UUID next = body.hasNonNull("nextCursor") ? UUID.fromString(body.path("nextCursor").textValue()) : null;
      return new Page(scope, List.copyOf(items), next);
    } catch (IllegalArgumentException | NullPointerException | java.time.DateTimeException invalid) {
      throw error(503);
    }
  }

  private String workload(String scope) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", CLIENT);
    form.add("client_secret", secret);
    form.add("scope", scope);
    try {
      var response = keycloak.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().toEntity(JsonNode.class);
      JsonNode body = response.getBody();
      if (response.getStatusCode().value() != 200 || body == null
          || !"Bearer".equalsIgnoreCase(body.path("token_type").asText())) throw error(503);
      String token = body.path("access_token").textValue();
      if (token == null || !token.matches("[A-Za-z0-9_.-]+") || token.length() > 16384) throw error(503);
      return token;
    } catch (RestClientException failure) {
      throw error(503);
    }
  }

  private static boolean valid(URI value, boolean allowHttp) {
    return value != null && value.getHost() != null && value.getUserInfo() == null
        && value.getQuery() == null && value.getFragment() == null
        && ("https".equals(value.getScheme()) || allowHttp && "http".equals(value.getScheme()));
  }

  private static ResponseStatusException error(int status) {
    return new ResponseStatusException(HttpStatus.valueOf(status));
  }
}
