package com.bookrush.readerbffservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ReaderBffControllerTest {
  private final ReaderBffController controller =
      new ReaderBffController("http://recommendation", "http://state", "http://content");

  @Test
  void everyCompositionRouteRequiresAnAuthenticatedReader() {
    var request = new MockHttpServletRequest();
    assertThrows(ResponseStatusException.class, () -> controller.feed(20, null, request));
    assertThrows(ResponseStatusException.class, () -> controller.library(null, request));
    assertThrows(ResponseStatusException.class, () -> controller.bookmarks(java.util.UUID.randomUUID(), null, request));
  }

  @Test
  void blankPrincipalIsNotAccepted() {
    Principal blank = () -> " ";
    assertThrows(ResponseStatusException.class, () -> controller.streak(blank, new MockHttpServletRequest()));
  }
}
