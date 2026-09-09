package com.bookrush.ingestion.catalog;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion.catalog")
public record CanonicalCatalogProperties(@NotNull URI baseUrl, String serviceToken,
    @NotNull Duration requestTimeout) {}
