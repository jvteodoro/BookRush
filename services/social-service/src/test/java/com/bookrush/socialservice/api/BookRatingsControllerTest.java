package com.bookrush.socialservice.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class BookRatingsControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final UUID book = UUID.randomUUID();
  private HttpServer catalog;
  private MockMvc mvc;
  private int catalogStatus = 200;

  @BeforeEach
  void setup() throws Exception {
    catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    catalog.createContext("/api/v1/books/", exchange -> {
      exchange.sendResponseHeaders(catalogStatus, -1);
      exchange.close();
    });
    catalog.start();
    mvc = MockMvcBuilders.standaloneSetup(new BookRatingsController(jdbc, RestClient.builder(),
        "http://127.0.0.1:" + catalog.getAddress().getPort())).build();
  }

  @AfterEach
  void cleanup() { catalog.stop(0); }

  @Test
  void allMethodsRequireIdentity() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(put(path()).contentType("application/json").content("{\"stars\":5}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete(path())).andExpect(status().isUnauthorized());
    verifyNoInteractions(jdbc);
  }

  @Test
  void invalidScoresNeverWrite() throws Exception {
    for (String value : new String[]{"0", "6", "1.5", "null", "\"bad\""}) {
      mvc.perform(put(path()).principal(() -> "reader").contentType("application/json")
          .content("{\"stars\":" + value + "}")).andExpect(status().isBadRequest());
    }
    mvc.perform(put(path()).principal(() -> "reader").contentType("application/json")
        .content("{}")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/social/books/invalid/ratings").principal(() -> "reader"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(jdbc);
  }

  @Test
  void catalogErrorsNeverWrite() throws Exception {
    catalogStatus = 404;
    mvc.perform(put(path()).principal(() -> "reader").contentType("application/json")
        .content("{\"stars\":5}")).andExpect(status().isNotFound());
    catalogStatus = 500;
    mvc.perform(get(path()).principal(() -> "reader")).andExpect(status().isServiceUnavailable());
    verifyNoInteractions(jdbc);
  }

  @Test
  @SuppressWarnings("unchecked")
  void writesAndDeletesUseAuthenticatedSubjectAndReturnSummary() throws Exception {
    var summary = new BookRatingsController.Summary(book, 2, new java.math.BigDecimal("4.50"), 5);
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq("reader"), eq(book))).thenReturn(summary);
    mvc.perform(put(path()).principal(() -> "reader").contentType("application/json")
        .content("{\"stars\":5,\"subject\":\"someone-else\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalRatings").value(2))
        .andExpect(jsonPath("$.averageRating").value(4.5)).andExpect(jsonPath("$.myRating").value(5));
    verify(jdbc).update(contains("on conflict(subject_key,book_id)"), eq("reader"), eq(book), eq(5));
    mvc.perform(delete(path()).principal(() -> "reader")).andExpect(status().isOk());
    verify(jdbc).update("delete from social.book_rating where subject_key=? and book_id=?", "reader", book);
  }

  private String path() { return "/api/v1/social/books/" + book + "/ratings"; }
}
