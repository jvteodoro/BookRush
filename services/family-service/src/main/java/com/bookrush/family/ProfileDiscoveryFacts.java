package com.bookrush.family;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class ProfileDiscoveryFacts {
  static final String PATH = "/api/internal/v1/product-access/profile-discovery";
  record Query(@NotNull @Size(max = 500) Set<@NotBlank @Size(max = 256) String> subjects) {}
  record Result(Set<String> subjects, String correlationId, Instant validUntil) {}

  static boolean isConsumer(String client) {
    return "bookrush-reader-profile-product-access-s2s".equals(client)
        || "bookrush-social-product-access-s2s".equals(client);
  }

  private final KeycloakAgeFacts ages;
  private final Clock clock;
  private final boolean requireVerification;

  ProfileDiscoveryFacts(KeycloakAgeFacts ages, Clock clock,
      @Value("${family.product-access-age-verification-required}") boolean requireVerification) {
    this.ages = ages;
    this.clock = clock;
    this.requireVerification = requireVerification;
  }

  @PostMapping("/api/internal/v1/product-access/profile-discovery")
  ResponseEntity<Result> discover(@RequestBody @Valid Query query,
      @RequestHeader("X-Correlation-ID") String correlation) {
    try {
      com.bookrush.platform.core.CorrelationId.parse(correlation);
    } catch (IllegalArgumentException invalid) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
    var adults = ages.discoverableProfiles(query.subjects(), requireVerification);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
        .body(new Result(adults, correlation, clock.instant().plusSeconds(5)));
  }
}
