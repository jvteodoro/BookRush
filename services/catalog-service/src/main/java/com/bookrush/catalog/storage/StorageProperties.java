package com.bookrush.catalog.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Externalized S3 configuration. Public endpoint is used during signing, never rewritten afterward. */
@Validated
@ConfigurationProperties("storage")
public record StorageProperties(
    @DefaultValue("false") boolean enabled,
    URI endpoint, URI publicEndpoint,
    @DefaultValue("us-east-1") @NotBlank String region,
    String accessKey, String secretKey,
    @DefaultValue("true") boolean pathStyle,
    @DefaultValue("300") @Min(1) @Max(3600) int urlTtlSeconds,
    @DefaultValue("104857600") @Min(1) @Max(104857600) long maxUploadBytes,
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("") String adminToken,
    @Valid Buckets buckets) {
  /**
   * Configuration binding may omit the nested bucket object when a test or a
   * deployment only overrides endpoint credentials. Keep the operational
   * defaults in one place so storage startup never dereferences a null group.
   */
  public StorageProperties {
    String source = buckets == null ? null : buckets.source();
    String publicBucket = buckets == null ? null : buckets.publicBucket();
    String processing = buckets == null ? null : buckets.processing();
    String ml = buckets == null ? null : buckets.ml();
    String raw = buckets == null ? null : buckets.raw();
    buckets = new Buckets(defaultBucket(source, "books-source"),
        defaultBucket(publicBucket, "books-public"),
        defaultBucket(processing, "books-processing"),
        defaultBucket(ml, "books-ml"),
        defaultBucket(raw, "books-raw"));
  }

  private static String defaultBucket(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public record Buckets(
      @DefaultValue("books-source") @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String source,
      @DefaultValue("books-public") @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String publicBucket,
      @DefaultValue("books-processing") @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String processing,
      @DefaultValue("books-ml") @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String ml,
      @DefaultValue("books-raw") @Pattern(regexp="[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String raw) {
    public Buckets(String source, String publicBucket, String processing, String ml) {
      this(source, publicBucket, processing, ml, "books-raw");
    }
    public List<String> all() { return List.of(source, publicBucket, processing, ml, raw); }
    @AssertTrue public boolean isDistinct() { return all().stream().distinct().count()==5; }
  }
  @AssertTrue public boolean isCredentialPairValid() {
    return (accessKey == null || accessKey.isBlank()) == (secretKey == null || secretKey.isBlank());
  }
  @AssertTrue public boolean isAdminTokenValid() { return !enabled || (adminToken!=null && adminToken.length()>=32); }
  @AssertTrue public boolean isTimeoutValid() { return timeout!=null && !timeout.isNegative() && !timeout.isZero() && timeout.compareTo(Duration.ofMinutes(5))<=0; }
  @AssertTrue public boolean isEndpointValid() { return valid(endpoint) && valid(publicEndpoint) && (!enabled || endpoint==null || publicEndpoint!=null); }
  private static boolean valid(URI uri) {
    return uri==null || (List.of("http","https").contains(uri.getScheme()) && uri.getHost()!=null
        && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null
        && (uri.getPath().isEmpty() || uri.getPath().equals("/")));
  }
  @Override public String toString() { return "StorageProperties[redacted]"; }
}
