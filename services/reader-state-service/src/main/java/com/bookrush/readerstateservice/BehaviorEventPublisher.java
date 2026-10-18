package com.bookrush.readerstateservice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BehaviorEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(BehaviorEventPublisher.class);
  private final RestClient client;

  public BehaviorEventPublisher(RestClient.Builder builder,
      @Value("${bookrush.behavior-url:http://behavior-service:8097}") String url) {
    client = builder.baseUrl(url).build();
  }

  public void publish(String type, UUID bookId, Map<String, ?> payload, String authorization) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventKey", type + ":" + UUID.randomUUID());
    event.put("eventType", type);
    if (bookId != null) event.put("bookId", bookId);
    event.put("occurredAt", Instant.now().toString());
    event.put("payload", payload == null ? Map.of() : payload);
    Map<String, Object> request = Map.of("events", List.of(event));
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        client.post().uri("/api/v1/behavior/events")
            .headers(headers -> { if (authorization != null && !authorization.isBlank())
              headers.setBearerAuth(authorization.replaceFirst("(?i)^Bearer\\s+", "")); })
            .body(request).retrieve().toBodilessEntity();
        return;
      } catch (RuntimeException exception) {
        if (attempt == 3) log.warn("behavior event delivery failed type={} attempts={}", type, attempt);
      }
    }
  }
}
