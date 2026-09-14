package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.matching.MatchingEngine;
import com.bookrush.ingestion.matching.OpenLibraryMatchingService;
import com.bookrush.ingestion.source.OpenLibraryDumpParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {
  @Test void highEvidenceCandidateIsAcceptedWithStableTieBreak() {
    var engine = new MatchingEngine();
    var input = new MatchingEngine.Input("Pride and Prejudice", "Jane Austen", "en");
    var decision = engine.decide(input, List.of(
        new MatchingEngine.Candidate("OL1W", "Pride and Prejudice", "Jane Austen", "en", false),
        new MatchingEngine.Candidate("OL2W", "Pride", "Jane Austen", "en", false)));
    assertEquals("AUTO_ACCEPTED", decision.status());
    assertEquals("OL1W", decision.selectedId());
  }

  @Test void missingFieldsDoNotGetReweightedAndContradictionNeedsReview() {
    var engine = new MatchingEngine();
    var decision = engine.decide(new MatchingEngine.Input("A", null, null),
        List.of(new MatchingEngine.Candidate("x", "A", "Someone", "en", true)));
    assertEquals("NO_MATCH", decision.status());
  }

  @Test void openLibraryDecisionKeepsRevisionAndEvidenceWithoutAutoMergingAmbiguity() throws Exception {
    var row = new OpenLibraryDumpParser.DumpRecord("/works/OL1W", "OL1W", "7", "2024",
        new ObjectMapper().readTree("{\"type\":{\"key\":\"/type/work\"},\"title\":\"Pride\"}"), 3);
    var candidates = List.of(
        new MatchingEngine.Candidate("book-a", "Pride", "Jane Austen", "en", false),
        new MatchingEngine.Candidate("book-b", "Pride", "Jane Austen", "en", false));
    var result = new OpenLibraryMatchingService().decide(row,
        new MatchingEngine.Input("Pride", "Jane Austen", "en"), candidates);
    assertEquals("OL1W", result.externalId());
    assertEquals("7", result.revision());
    assertEquals(64, result.evidenceSha256().length());
    assertEquals("REVIEW_REQUIRED", result.decision().status());
  }
}
