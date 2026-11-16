package com.bookrush.readerbffservice.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.web.filter.CorsFilter;

class ReaderBookmarkCorsTest {
  @Test
  void bookmarkMutationsAllowOnlyTheConfiguredOrigin() throws Exception {
    var filter = new CorsFilter(new ReaderBffSecurityConfiguration()
        .readerBffCorsConfigurationSource("https://reader.example"));
    for (String method : new String[] { "PUT", "DELETE" }) {
      var request = new MockHttpServletRequest("OPTIONS", "/api/v1/reader/books/book/bookmarks/mark");
      request.addHeader("Origin", "https://reader.example");
      request.addHeader("Access-Control-Request-Method", method);
      request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertEquals(200, response.getStatus());
      assertEquals("https://reader.example", response.getHeader("Access-Control-Allow-Origin"));
      assertTrue(response.getHeader("Access-Control-Allow-Methods").contains(method));
    }
    var request = new MockHttpServletRequest("OPTIONS", "/api/v1/reader/books/book/bookmarks/mark");
    request.addHeader("Origin", "https://untrusted.example");
    request.addHeader("Access-Control-Request-Method", "DELETE");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertEquals(403, response.getStatus());
    assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }
}
