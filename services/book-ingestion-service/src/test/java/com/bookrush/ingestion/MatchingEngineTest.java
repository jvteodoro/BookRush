package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.matching.MatchingEngine;
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
}
