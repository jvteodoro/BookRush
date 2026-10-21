package com.bookrush.recommendationservice.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.retrieval")
public record CandidateRetrievalProperties(int maxCandidates, int pageSize, int maxPages,
    String version) {
  public CandidateRetrievalProperties {
    maxCandidates = maxCandidates < 1 ? 500 : Math.min(maxCandidates, 2_000);
    pageSize = pageSize < 1 ? 100 : Math.min(pageSize, 100);
    maxPages = maxPages < 1 ? 5 : Math.min(maxPages, 20);
    version = version == null || version.isBlank() ? "catalog-page-v1" : version;
  }

  public int effectiveLimit(int requestedLimit) {
    return Math.max(1, Math.min(requestedLimit, maxCandidates));
  }
}
