package com.bookrush.ingestion.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ingestion.security")
public record IngestionSecurityProperties(
    boolean enabled,
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotBlank String jwkSetUri,
    boolean localFixtureMode,
    String localFixtureToken) {}
