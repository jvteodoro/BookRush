package com.bookrush.publisherservice.api;

import com.bookrush.publisherservice.PublisherMetricsClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class PublisherMetricsClientTest {
  @Test void authenticatesWithDedicatedScopeAndCachesToken() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var tokens = new AtomicInteger(); var reads = new AtomicInteger();
    UUID book = UUID.randomUUID();
    server.createContext("/token", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
      if (!body.contains("scope=bookrush.publisher.metrics.read")) { exchange.sendResponseHeaders(400,-1); exchange.close(); return; }
      tokens.incrementAndGet(); byte[] data = "{\"access_token\":\"test-token\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
    });
    server.createContext("/api/internal/v1/publisher/metrics/books/"+book, exchange -> {
      if (!"Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) { exchange.sendResponseHeaders(401,-1); exchange.close(); return; }
      reads.incrementAndGet(); byte[] data = "[{\"day\":\"2026-11-10\",\"opens\":3}]".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
    });
    server.start();
    try {
      String base = "http://127.0.0.1:"+server.getAddress().getPort();
      var client = new PublisherMetricsClient(base,base+"/token","publisher-test","test-secret");
      assertEquals(3,client.metrics(book,30).getFirst().get("opens")); client.metrics(book,30);
      assertEquals(1,tokens.get()); assertEquals(2,reads.get());
    } finally { server.stop(0); }
  }
  @Test void missingCredentialsAreUnavailableNotZero() {
    var client = new PublisherMetricsClient("http://127.0.0.1:1","","test","");
    assertEquals(503,assertThrows(ResponseStatusException.class,()->client.metrics(UUID.randomUUID(),30)).getStatusCode().value());
  }
}
