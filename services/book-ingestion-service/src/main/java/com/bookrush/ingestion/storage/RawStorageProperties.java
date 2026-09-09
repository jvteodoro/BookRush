package com.bookrush.ingestion.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion.storage")
public record RawStorageProperties(
    @NotNull URI endpoint,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    boolean pathStyle,
    @NotBlank String rawBucket,
    @NotNull Duration timeout) {}
