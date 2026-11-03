package com.bookrush.recommendationservice.retrieval;

/** Retrieves a bounded, versioned candidate snapshot for the ranker. */
public interface CandidateRetriever {
  CandidateSet retrieve(int requestedLimit);

  /** Maximum bounded universe configured for the live retriever. */
  default int configuredLimit() { return 500; }
}
