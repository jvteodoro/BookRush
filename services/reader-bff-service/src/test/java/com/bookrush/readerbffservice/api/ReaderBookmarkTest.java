package com.bookrush.readerbffservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReaderBookmarkTest {
  @Test
  void forwardsRenameAndDeleteWithBearerAndPreservesNotFound() throws Exception {
    String book = "11111111-1111-4111-8111-111111111111";
    String mark = "22222222-2222-4222-8222-222222222222";
    String path = "/api/v1/reader/books/" + book + "/bookmarks/" + mark;
    var calls = new ArrayList<String>();
    var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    upstream.createContext(path, exchange -> {
      calls.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (exchange.getRequestMethod().equals("DELETE")) { exchange.sendResponseHeaders(204, -1); exchange.close(); return; }
      byte[] response = ("{\"id\":\"" + mark + "\",\"name\":\"Trecho\"}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(body.contains("missing") ? 404 : 200, response.length);
      try (var output = exchange.getResponseBody()) { output.write(response); }
    });
    upstream.start();
    try {
      var mvc = MockMvcBuilders.standaloneSetup(new ReaderBffController("http://recommendation",
          "http://127.0.0.1:" + upstream.getAddress().getPort(), "http://content")).build();
      mvc.perform(put(path).principal(() -> "reader").header("Authorization", "Bearer fixture")
          .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Trecho\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Trecho"));
      mvc.perform(delete(path).principal(() -> "reader").header("Authorization", "Bearer fixture"))
          .andExpect(status().isNoContent()).andExpect(content().string(""));
      mvc.perform(put(path).principal(() -> "reader").header("Authorization", "Bearer fixture")
          .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"missing\"}"))
          .andExpect(status().isNotFound());
      assertEquals(List.of("PUT Bearer fixture", "DELETE Bearer fixture", "PUT Bearer fixture"), calls);
    } finally { upstream.stop(0); }
  }
}
