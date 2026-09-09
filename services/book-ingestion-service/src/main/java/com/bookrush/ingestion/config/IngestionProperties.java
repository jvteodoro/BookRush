package com.bookrush.ingestion.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
    @NotNull IngestionRole role,
    boolean schedulerEnabled,
    @NotBlank String stagingRoot,
    @Min(1) int workerThreads,
    @Min(1) int chunkSize,
    @Min(1) int leaseSeconds,
    @Min(1) int heartbeatSeconds,
    @Min(1) long maxRecordBytes,
    @Min(1) long maxAssetBytes,
    @Min(1) long maxUncompressedBytes,
    @Min(1) long maxEntries,
    @Min(1) long maxProcessingSeconds,
    @NotNull Duration requestTimeout,
    @Min(0) int maxRequestsPerSecond,
    @Min(1) int retentionDays) {

  public Path stagingPath() {
    return Path.of(stagingRoot).toAbsolutePath().normalize();
  }

  public IngestionProperties {
    if (heartbeatSeconds >= leaseSeconds) {
      throw new IllegalArgumentException("ingestion.heartbeat-seconds must be less than lease-seconds");
    }
  }
}
