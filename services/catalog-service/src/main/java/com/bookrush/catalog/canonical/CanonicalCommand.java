package com.bookrush.catalog.canonical;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;

public record CanonicalCommand(
    @NotBlank String operationKey,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String requestHash,
    @PositiveOrZero long fenceToken,
    @NotBlank String sourceCode,
    @NotBlank String identifierType,
    @NotBlank String identifierValue,
    @NotBlank String title,
    String language,
    String description,
    JsonNode metadata,
    String publisher,
    Integer publicationYear,
    String authorName,
    String authorRole) {}
