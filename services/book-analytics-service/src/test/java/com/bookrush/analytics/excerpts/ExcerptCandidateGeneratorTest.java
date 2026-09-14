package com.bookrush.analytics.excerpts;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ExcerptCandidateGeneratorTest {
 @Test void usesCodePointOffsetsAndIsDeterministic() {
  var text="Olá😀. Segunda pergunta? Terceira frase!";
  var first=ExcerptCandidateGenerator.generate(text,3,1,"v1");
  assertEquals(first,ExcerptCandidateGenerator.generate(text,3,1,"v1"));
  assertEquals(0,first.getFirst().startCodepoint()); assertTrue(first.getFirst().endCodepoint()>first.getFirst().startCodepoint());
  assertTrue(first.stream().allMatch(c -> c.textSha256().length()==64));
 }
 @Test void skipsIndexBoilerplate() { assertTrue(ExcerptCandidateGenerator.generate("Contents. A meaningful sentence here.",2,1,"v1").stream().noneMatch(c -> c.text().toLowerCase().startsWith("contents"))); }
}
