package com.bookrush.recommendationservice.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feed.retrieval")
public record CandidateRetrievalProperties(int maxCandidates, int pageSize, int maxPages,
    String version, String unionVersion, int semanticQuota, int globalQuota,
    int trendingQuota, int explorationQuota) {
  public CandidateRetrievalProperties {
    maxCandidates = maxCandidates < 1 ? 500 : Math.min(maxCandidates, 2_000);
    pageSize = pageSize < 1 ? 100 : Math.min(pageSize, 100);
    maxPages = maxPages < 1 ? 5 : Math.min(maxPages, 20);
    version = version == null || version.isBlank() ? "catalog-page-v1" : version;
    unionVersion = unionVersion == null || unionVersion.isBlank() ? "union-v1" : unionVersion;
    semanticQuota = semanticQuota < 0 ? 200 : Math.min(semanticQuota, maxCandidates);
    globalQuota = globalQuota < 0 ? 150 : Math.min(globalQuota, maxCandidates);
    trendingQuota = trendingQuota < 0 ? 100 : Math.min(trendingQuota, maxCandidates);
    explorationQuota = explorationQuota < 0 ? 50 : Math.min(explorationQuota, maxCandidates);
  }

  public CandidateRetrievalProperties(int maxCandidates, int pageSize, int maxPages, String version) {
    this(maxCandidates, pageSize, maxPages, version, "union-v1", 200, 150, 100, 50);
  }

  public int effectiveLimit(int requestedLimit) {
    return Math.max(1, Math.min(requestedLimit, maxCandidates));
  }
}
