package com.bookrush.ingestion.storage;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public final class S3RawObjectStore implements RawObjectStore {
  private final S3Client client;
  private final RawStorageProperties properties;

  public S3RawObjectStore(S3Client client, RawStorageProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public void put(String objectKey, InputStream content, long contentLength, String sha256) {
    if (contentLength < 0 || contentLength > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid raw object length");
    var bytes = new ByteArrayOutputStream((int) contentLength);
    try {
      content.transferTo(bytes);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("cannot read raw object", e);
    }
    if (bytes.size() != contentLength) throw new IllegalStateException("raw object length changed while uploading");
    client.putObject(PutObjectRequest.builder().bucket(properties.rawBucket()).key(objectKey)
        .contentType("application/octet-stream").metadata(java.util.Map.of("sha256", sha256))
        .build(), RequestBody.fromBytes(bytes.toByteArray()));
  }

  @Override
  public Optional<RawObjectMetadata> head(String objectKey) {
    try {
      var response = client.headObject(HeadObjectRequest.builder().bucket(properties.rawBucket()).key(objectKey).build());
      return Optional.of(new RawObjectMetadata(properties.rawBucket(), objectKey, response.contentLength(), response.eTag()));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public InputStream get(String objectKey) {
    return client.getObject(r -> r.bucket(properties.rawBucket()).key(objectKey));
  }
}
