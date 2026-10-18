package com.bookrush.readerbffservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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

  @Test
  void downstreamClientErrorsKeepTheirHttpStatus() {
    var exception = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null);

    var translated = controller.translateUpstreamFailure("content dependency rejected request", exception);

    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.NOT_FOUND, translated.getStatusCode());
  }

  @Test
  void downstreamServerErrorsBecomeBadGateway() {
    var exception = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "server error", null, null, null);

    var translated = controller.translateUpstreamFailure("reader dependency rejected request", exception);

    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_GATEWAY, translated.getStatusCode());
  }
}
