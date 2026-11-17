package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookrush.publisherservice.BehaviorEventPublisher;
import com.bookrush.publisherservice.PublisherIngestionClient;
import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Explicit integration test; TEST_DATABASE_URL must identify a disposable PostgreSQL database. */
class PublisherSubmissionPostgresIT {
  @Test void finalizedUploadAndRetryPreserveOwnershipAndWorkflow() {
    var dataSource = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"), "bookrush_test", "test-only-not-for-production");
    var flyway = Flyway.configure().dataSource(dataSource).schemas("publisher").load();
    flyway.migrate();
    var jdbc = new JdbcTemplate(dataSource);
    var ingestion = mock(PublisherIngestionClient.class);
    var controller = new PublisherController(jdbc, ingestion, mock(BehaviorEventPublisher.class));
    var s3 = mock(S3Client.class);
    var uploads = new PublisherUploadController(jdbc, mock(S3Presigner.class), s3, new StorageProperties());
    String owner = "publisher-" + UUID.randomUUID();
    var request = new MockHttpServletRequest();
    var draft = (Map<?, ?>) controller.create(new PublisherController.Submission("Own book", null, null, null), () -> owner, request);
    UUID id = (UUID) draft.get("id"), uploadId = UUID.randomUUID();
    try {
      assertEquals(409, assertThrows(ResponseStatusException.class,
          () -> controller.submit(id, () -> owner, request)).getStatusCode().value());
      jdbc.update("INSERT INTO publisher.staged_upload(id,submission_id,subject_key,object_key,content_type) VALUES (?,?,?,?,?)",
          uploadId, id, owner, "fixture/" + uploadId + ".epub", "application/epub+zip");
      assertEquals(404, assertThrows(ResponseStatusException.class,
          () -> uploads.finalizeUpload(id, uploadId, Map.of("sha256", "a".repeat(64)), () -> "other-owner")).getStatusCode().value());
      when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(42L).build());
      uploads.finalizeUpload(id, uploadId, Map.of("sha256", "a".repeat(64)), () -> owner);
      assertEquals("UPLOADED", jdbc.queryForObject("SELECT status FROM publisher.submission WHERE id=?", String.class, id));
      doThrow(new IllegalStateException("fixture failure")).when(ingestion).process(any(), any(), any(), any(), any(), any());
      assertEquals(503, assertThrows(ResponseStatusException.class,
          () -> controller.submit(id, () -> owner, request)).getStatusCode().value());
      assertEquals("UPLOADED", jdbc.queryForObject("SELECT status FROM publisher.submission WHERE id=?", String.class, id));
      doNothing().when(ingestion).process(any(), any(), any(), any(), any(), any());
      assertEquals("PROCESSING", ((Map<?, ?>) controller.submit(id, () -> owner, request)).get("status"));
      assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM publisher.publication_review WHERE submission_id=? AND status='PENDING'", Integer.class, id));
      controller.submit(id, () -> owner, request);
      verify(ingestion, times(2)).process(eq(id), any(), any(), any(), any(), any());
      assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM publisher.publication_review WHERE submission_id=?", Integer.class, id));
      assertEquals(409, assertThrows(ResponseStatusException.class,
          () -> uploads.request(id, Map.of("filename", "new.pdf", "contentType", "application/pdf"), () -> owner)).getStatusCode().value());
      // The ingestion callback may commit even if its HTTP response is lost.
      jdbc.update("DELETE FROM publisher.publication_review WHERE submission_id=?", id);
      jdbc.update("UPDATE publisher.submission SET status='UPLOADED' WHERE id=?", id);
      doAnswer(invocation -> {
        jdbc.update("UPDATE publisher.submission SET status='PROCESSING' WHERE id=?", id);
        throw new IllegalStateException("fixture lost response after callback");
      }).when(ingestion).process(any(), any(), any(), any(), any(), any());
      assertEquals(503, assertThrows(ResponseStatusException.class,
          () -> controller.submit(id, () -> owner, request)).getStatusCode().value());
      assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM publisher.submission WHERE id=?", String.class, id));
      controller.submit(id, () -> owner, request);
      assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM publisher.publication_review WHERE submission_id=?", Integer.class, id));
      verify(ingestion, times(3)).process(eq(id), any(), any(), any(), any(), any());
      flyway.validate();
    } finally {
      jdbc.update("DELETE FROM publisher.publication_review WHERE submission_id=?", id);
      jdbc.update("DELETE FROM publisher.staged_upload WHERE submission_id=?", id);
      jdbc.update("DELETE FROM publisher.submission WHERE id=?", id);
      jdbc.update("DELETE FROM publisher.membership WHERE subject_key=?", owner);
    }
  }
}
