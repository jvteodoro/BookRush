package com.bookrush.recommendationservice.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogCandidateRetrieverTest {
  @Test
  void retrievesBeyondTheFormerFirstPageCeilingAndDeduplicatesBooks() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID first = UUID.randomUUID();
    UUID outsideFormerCeiling = UUID.randomUUID();
    server.expect(requestTo("http://catalog/api/v1/books?page=0&size=2"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"items\":[{\"id\":\"" + first
            + "\",\"title\":\"first\"},{\"id\":\"" + outsideFormerCeiling
            + "\",\"title\":\"second\"}]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://catalog/api/v1/books?page=1&size=2"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"items\":[{\"id\":\"" + outsideFormerCeiling
            + "\",\"title\":\"duplicate\"},{\"id\":\"" + UUID.randomUUID()
            + "\",\"title\":\"third\"}]}", MediaType.APPLICATION_JSON));
    var properties = new CandidateRetrievalProperties(3, 2, 3, "test-v1");
    var retriever = new CatalogCandidateRetriever(builder, "http://catalog", properties,
        new SimpleMeterRegistry());

    CandidateSet result = retriever.retrieve(5);

    assertEquals(3, result.books().size());
    assertEquals(outsideFormerCeiling.toString(), result.books().get(1).get("id"));
    assertEquals("second", result.books().get(1).get("title"));
    assertTrue(result.canonicalBookIds().contains(outsideFormerCeiling.toString()));
    assertEquals("catalog", result.source());
    server.verify();
  }

  @Test
  void preservesBooksWithoutEmbeddingsAndStopsAtShortPage() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID book = UUID.randomUUID();
    server.expect(requestTo("http://catalog/api/v1/books?page=0&size=3"))
        .andRespond(withSuccess("{\"items\":[{\"id\":\"" + book
            + "\",\"title\":\"no embedding\"}]}", MediaType.APPLICATION_JSON));
    var retriever = new CatalogCandidateRetriever(builder, "http://catalog",
        new CandidateRetrievalProperties(20, 3, 5, "test-v1"), new SimpleMeterRegistry());

    CandidateSet result = retriever.retrieve(20);

    assertEquals(List.of(book.toString()), result.canonicalBookIds());
    assertTrue(!result.books().get(0).containsKey("embedding"));
    server.verify();
  }
}
