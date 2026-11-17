package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class PublisherUploadControllerTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final S3Client s3 = mock(S3Client.class);
  private final S3Presigner signer = mock(S3Presigner.class);
  private final PublisherUploadController controller = new PublisherUploadController(jdbc, signer, s3, new StorageProperties());
  private final UUID id = UUID.randomUUID(), uploadId = UUID.randomUUID();
  private void editable() {
    when(jdbc.queryForList(contains("FROM publisher.submission"), eq(id), eq("owner"))).thenReturn(List.of(Map.of("status", "DRAFT")));
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("owner"))).thenReturn("ACTIVE");
  }
  @Test void unsupportedFormatDoesNotReserveStorage() {
    editable();
    assertEquals(415, assertThrows(ResponseStatusException.class, () -> controller.request(id, Map.of("contentType", "text/plain"), () -> "owner")).getStatusCode().value());
    verifyNoInteractions(signer, s3);
  }
  @Test void foreignDraftCannotReserveStorage() {
    when(jdbc.queryForList(anyString(), eq(id), eq("owner"))).thenReturn(List.of());
    assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.request(id, Map.of("contentType", "application/pdf"), () -> "owner")).getStatusCode().value());
    verifyNoInteractions(signer, s3);
  }
  @Test void inactiveMembershipCannotUpload() {
    editable();
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("owner"))).thenReturn("REVOKED");
    assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.request(id, Map.of("contentType", "application/pdf"), () -> "owner")).getStatusCode().value());
    verifyNoInteractions(signer, s3);
  }
  @Test void invalidChecksumDoesNotFinalize() {
    editable();
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.finalizeUpload(id, uploadId, Map.of("sha256", "bad-hash"), () -> "owner")).getStatusCode().value());
    verifyNoInteractions(s3);
  }
  @Test void emptyStorageObjectDoesNotFinalize() {
    editable();
    when(jdbc.queryForList(contains("FROM publisher.staged_upload"), eq(uploadId), eq(id), eq("owner"))).thenReturn(List.of(Map.of("object_key", "book.epub")));
    when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(0L).build());
    assertEquals(422, assertThrows(ResponseStatusException.class, () -> controller.finalizeUpload(id, uploadId, Map.of("sha256", "a".repeat(64)), () -> "owner")).getStatusCode().value());
    verify(jdbc, never()).update(contains("SET status='FINALIZED'"), any(), any(), any());
  }
}
