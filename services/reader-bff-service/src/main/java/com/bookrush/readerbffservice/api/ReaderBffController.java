package com.bookrush.readerbffservice.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated composition gateway for reader-facing state and content. */
@RestController
@RequestMapping("/api/v1/reader")
public class ReaderBffController {
  private final RestClient recommendations;
  private final RestClient state;
  private final RestClient content;

  public ReaderBffController(
      @Value("${bookrush.recommendation-url:http://recommendation-service:8098}") String recommendationUrl,
      @Value("${bookrush.reader-state-url:http://reader-state-service:8094}") String stateUrl,
      @Value("${bookrush.content-url:http://book-content-service:8093}") String contentUrl) {
    recommendations = client(recommendationUrl);
    state = client(stateUrl);
    content = client(contentUrl);
  }

  @GetMapping("/feed")
  public Object feed(@RequestParam(defaultValue = "20") int size, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(recommendations, "/api/v1/recommendations/feed", request, Map.of("size", Math.min(Math.max(size, 1), 100)));
  }

  @GetMapping("/recommendations")
  public Object recommendations(Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(recommendations, "/api/v1/recommendations/feed", request, Map.of("size", 20));
  }

  @GetMapping("/library")
  public Object library(Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(state, "/api/v1/reader/library", request, Map.of());
  }

  @GetMapping("/recent")
  public Object recent(Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(state, "/api/v1/reader/recent", request, Map.of());
  }

  @GetMapping("/streak")
  public Object streak(Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(state, "/api/v1/reader/streak", request, Map.of());
  }

  @GetMapping("/books/{bookId}/progress")
  public Object progress(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(state, "/api/v1/reader/books/" + bookId + "/progress", request, Map.of());
  }

  @GetMapping("/books/{bookId}/bookmarks")
  public Object bookmarks(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(state, "/api/v1/reader/books/" + bookId + "/bookmarks", request, Map.of());
  }

  @PutMapping("/library/{bookId}")
  public Object saveLibrary(@PathVariable UUID bookId, @RequestBody Object body, Principal principal,
      HttpServletRequest request) {
    requireReader(principal);
    return mutate(state, HttpMethod.PUT, "/api/v1/reader/library/" + bookId, request, body);
  }

  @DeleteMapping("/library/{bookId}")
  public Object removeLibrary(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return mutate(state, HttpMethod.DELETE, "/api/v1/reader/library/" + bookId, request, null);
  }

  @PutMapping("/books/{bookId}/progress")
  public Object updateProgress(@PathVariable UUID bookId, @RequestBody Object body, Principal principal,
      HttpServletRequest request) {
    requireReader(principal);
    return mutate(state, HttpMethod.PUT, "/api/v1/reader/books/" + bookId + "/progress", request, body);
  }

  @PostMapping("/books/{bookId}/bookmarks")
  public Object addBookmark(@PathVariable UUID bookId, @RequestBody Object body, Principal principal,
      HttpServletRequest request) {
    requireReader(principal);
    return mutate(state, HttpMethod.POST, "/api/v1/reader/books/" + bookId + "/bookmarks", request, body);
  }

  @PostMapping("/books/{bookId}/opened")
  public Object opened(@PathVariable UUID bookId, @RequestBody(required = false) Object body, Principal principal,
      HttpServletRequest request) {
    requireReader(principal);
    return mutate(state, HttpMethod.POST, "/api/v1/reader/books/" + bookId + "/opened", request, body);
  }

  @PostMapping("/recommendations/impressions/{impressionId}/viewable")
  public Object viewable(@PathVariable UUID impressionId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return mutate(recommendations, HttpMethod.POST,
        "/api/v1/recommendations/impressions/" + impressionId + "/viewable", request, null);
  }

  @GetMapping("/books/{bookId}")
  public Object book(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(content, "/api/v1/content/books/" + bookId, request, Map.of());
  }

  @GetMapping("/books/{bookId}/publication.json")
  public Object publication(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(content, "/api/v1/content/books/" + bookId + "/publication.json", request, Map.of());
  }

  @GetMapping("/books/{bookId}/chapters")
  public Object chapters(@PathVariable UUID bookId, Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return get(content, "/api/v1/content/books/" + bookId + "/chapters", request, Map.of());
  }

  @GetMapping("/books/{bookId}/chapters/{chapterId}/content")
  public ResponseEntity<byte[]> chapterContent(@PathVariable UUID bookId, @PathVariable UUID chapterId,
      Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return getBytes(content, "/api/v1/content/books/" + bookId + "/chapters/" + chapterId + "/content", request);
  }

  @GetMapping("/books/{bookId}/resources/{*resource}")
  public ResponseEntity<byte[]> resource(@PathVariable UUID bookId, @PathVariable String resource,
      Principal principal, HttpServletRequest request) {
    requireReader(principal);
    return getBytes(content, "/api/v1/content/books/" + bookId + "/resources/" + resource, request);
  }

  private void requireReader(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
  }

  private RestClient client(String baseUrl) {
    var requestFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  private Object get(RestClient client, String path, HttpServletRequest request, Map<String, ?> variables) {
    String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
    try {
      var requestSpec = client.get();
      if (variables.isEmpty()) {
        // Avoid expanding an empty variable map in RestClient's URI builder.
        // The reader-state endpoints (library/recent/streak) have no query
        // parameters; using the literal path keeps the request identical to
        // the direct upstream call and avoids a misleading 502.
        requestSpec.uri(path);
      } else {
        requestSpec.uri(builder -> {
          var uri = builder.path(path);
          variables.forEach(uri::queryParam);
          return uri.build(variables);
        });
      }
      return requestSpec
          .headers(headers -> {
            if (bearer != null && !bearer.isBlank()) headers.set(HttpHeaders.AUTHORIZATION, bearer);
          })
          .retrieve()
          .body(Object.class);
    } catch (HttpStatusCodeException exception) {
      throw translateUpstreamFailure("reader dependency rejected request", exception);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "reader dependency unavailable", exception);
    }
  }

  private Object mutate(RestClient client, HttpMethod method, String path, HttpServletRequest request, Object body) {
    String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
    try {
      var requestSpec = client.method(method).uri(path).headers(headers -> {
        if (bearer != null && !bearer.isBlank()) headers.set(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
      });
      return body == null ? requestSpec.retrieve().body(Object.class) : requestSpec.body(body).retrieve().body(Object.class);
    } catch (HttpStatusCodeException exception) {
      throw translateUpstreamFailure("reader dependency rejected request", exception);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "reader dependency unavailable", exception);
    }
  }

  private ResponseEntity<byte[]> getBytes(RestClient client, String path, HttpServletRequest request) {
    String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
    try {
      return client.get().uri(path).headers(headers -> {
        if (bearer != null && !bearer.isBlank()) headers.set(HttpHeaders.AUTHORIZATION, bearer);
      }).retrieve().toEntity(byte[].class);
    } catch (HttpStatusCodeException exception) {
      throw translateUpstreamFailure("content dependency rejected request", exception);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "content dependency unavailable", exception);
    }
  }

  ResponseStatusException translateUpstreamFailure(
      String message, HttpStatusCodeException exception) {
    if (exception.getStatusCode().is4xxClientError()) {
      return new ResponseStatusException(exception.getStatusCode(), message, exception);
    }
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "reader dependency unavailable", exception);
  }
}
