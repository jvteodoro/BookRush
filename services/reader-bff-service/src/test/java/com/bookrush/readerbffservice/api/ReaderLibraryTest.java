package com.bookrush.readerbffservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReaderLibraryTest {
  @Test
  void savesWithoutARequestBodyAndKeepsExistingJsonClientsCompatible() throws Exception {
    String book = "11111111-1111-4111-8111-111111111111";
    String path = "/api/v1/reader/library/" + book;
    var bodies = new ArrayList<String>();
    var bearers = new ArrayList<String>();
    var methods = new ArrayList<String>();
    var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    upstream.createContext(path, exchange -> {
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      bearers.add(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
      methods.add(exchange.getRequestMethod());
      byte[] result = ("{\"bookId\":\"" + book + "\",\"saved\":true}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      exchange.sendResponseHeaders(200, result.length);
      try (var output = exchange.getResponseBody()) { output.write(result); }
    });
    upstream.start();
    try {
      var controller = new ReaderBffController("http://recommendation",
          "http://127.0.0.1:" + upstream.getAddress().getPort(), "http://content");
      var mvc = MockMvcBuilders.standaloneSetup(controller).build();
      mvc.perform(put(path).principal(() -> "reader")
              .header(HttpHeaders.AUTHORIZATION, "Bearer reader-test"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.bookId").value(book));
      mvc.perform(put(path).principal(() -> "reader")
              .header(HttpHeaders.AUTHORIZATION, "Bearer reader-test")
              .contentType(MediaType.APPLICATION_JSON).content("{\"saved\":true}"))
          .andExpect(status().isOk());
      assertEquals(List.of("", "{\"saved\":true}"), bodies);
      assertEquals(List.of("PUT", "PUT"), methods);
      assertEquals(List.of("Bearer reader-test", "Bearer reader-test"), bearers);
    } finally {
      upstream.stop(0);
    }
  }
}
