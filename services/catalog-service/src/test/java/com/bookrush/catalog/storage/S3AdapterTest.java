package com.bookrush.catalog.storage;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class S3AdapterTest {
  @Test void sanitizesSdkFailureAndDistinguishesMissingHead() {
    S3Client client=mock(S3Client.class);
    var metrics=new SimpleMeterRegistry();
    var adapter=new S3ObjectStorage(client,mock(S3Presigner.class),metrics);
    when(client.headObject(org.mockito.ArgumentMatchers.<java.util.function.Consumer<HeadObjectRequest.Builder>>any()))
        .thenThrow(S3Exception.builder().statusCode(404).message("secret signed URL").build());
    assertTrue(adapter.head(new ObjectStorage.Location("bucket","key")).isEmpty());
    doThrow(S3Exception.builder().statusCode(403).message("secret signed URL").build())
        .when(client).headObject(org.mockito.ArgumentMatchers.<java.util.function.Consumer<HeadObjectRequest.Builder>>any());
    var e=assertThrows(StorageFailure.class,()->adapter.head(new ObjectStorage.Location("bucket","key")));
    assertEquals(StorageFailure.Kind.UNAVAILABLE,e.kind());
    assertNull(e.getCause());assertFalse(e.getMessage().contains("secret"));
    assertEquals(2,metrics.get("bookrush.storage").tag("operation","head").timer().count());
    assertThrows(IllegalArgumentException.class,()->adapter.downloadUrl(new ObjectStorage.Location("bucket","key"),"a.txt",Duration.ofDays(1)));
  }
}
