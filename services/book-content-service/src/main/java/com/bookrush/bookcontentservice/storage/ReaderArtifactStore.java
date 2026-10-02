package com.bookrush.bookcontentservice.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Persists the immutable manifest snapshot used by a reader session. */
@Component
public final class ReaderArtifactStore {
  private static final Logger log = LoggerFactory.getLogger(ReaderArtifactStore.class);
  private final ObjectMapper mapper;
  private final S3Client client;
  private final String bucket;
  private final boolean enabled;

  public ReaderArtifactStore(ObjectMapper mapper,
      @Value("${bookrush.storage.endpoint:}") String endpoint,
      @Value("${bookrush.storage.region:us-east-1}") String region,
      @Value("${bookrush.storage.access-key:}") String accessKey,
      @Value("${bookrush.storage.secret-key:}") String secretKey,
      @Value("${bookrush.storage.path-style:true}") boolean pathStyle,
      @Value("${bookrush.storage.bucket:books-public}") String bucket) {
    this.mapper = mapper; this.bucket = bucket;
    this.enabled = !endpoint.isBlank() && !accessKey.isBlank() && !secretKey.isBlank();
    if (!enabled) { client = null; return; }
    var builder = S3Client.builder().region(Region.of(region)).forcePathStyle(pathStyle)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
    client = builder.build();
  }

  public String persist(String bookId, Map<String, Object> manifest) {
    if (!enabled) return null;
    try {
      byte[] bytes = mapper.writeValueAsBytes(manifest);
      String hash = sha256(bytes);
      String key = "reader-ready/" + bookId + "/manifest-" + hash + ".json";
      client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
          .contentType("application/webpub+json").metadata(Map.of("sha256", hash, "format", "readium-webpub-v1"))
          .build(), RequestBody.fromBytes(bytes));
      return key;
    } catch (Exception e) {
      // The manifest is still valid for the current reader session. Artifact
      // persistence is retried by the next content request and must not turn a
      // transient object-store outage into a reader-facing 500.
      log.warn("reader artifact persistence unavailable bookId={} exception={}", bookId,
          e.getClass().getSimpleName());
      return null;
    }
  }

  private static String sha256(byte[] value) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
    var result = new StringBuilder(64); for (byte b : digest) result.append(String.format("%02x", b)); return result.toString();
  }
}
