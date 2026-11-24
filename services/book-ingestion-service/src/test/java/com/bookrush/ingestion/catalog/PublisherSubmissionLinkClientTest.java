package com.bookrush.ingestion.catalog;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublisherSubmissionLinkClientTest {
  @Test
  void linksSynchronousUploadWithoutOptionalJobId() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var submission = UUID.randomUUID();
    var book = UUID.randomUUID();
    server.expect(requestTo("http://publisher/api/internal/v1/publisher/submissions/" + submission + "/catalog-link"))
        .andExpect(header("X-BookRush-Ingestion-Token", "fixture-only"))
        .andExpect(jsonPath("$.catalogBookId").value(book.toString()))
        .andExpect(jsonPath("$.ingestionJobId").doesNotExist())
        .andExpect(jsonPath("$.sourceExternalId").value(submission.toString()))
        .andRespond(withSuccess());
    var client = new PublisherSubmissionLinkClient(builder,
        new PublisherSubmissionLinkClient.Properties(URI.create("http://publisher"), "fixture-only"));
    client.link(submission, book, null, "ADMIN_UPLOAD", submission.toString());
    server.verify();
  }
}
