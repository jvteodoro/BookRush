package com.bookrush.analytics.excerpts;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CandidatePrunerTest {
 @Test void reportsReasonsAndReducesFixture(){
  var cs=ExcerptCandidateGenerator.generate("one two three four five. This is a sufficiently long sentence with enough words for the fixture.",2,1,"g1");
  var r=CandidatePruner.prune(cs,new CandidatePruner.Config(6,20,"p1"));
  assertTrue(r.rejected().stream().anyMatch(x->x.reason().equals("TOO_SHORT"))); assertTrue(r.accepted().size()<cs.size());
 }
}
