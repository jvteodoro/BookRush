package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class CorpusFrequencyBuilderTest {
 @Test void normalizedInputCreatesStableLanguageScopedArtifact(){
  var left=CorpusFrequencyBuilder.build("en","eligible-v1",List.of("The fox", "the fox."),0.5);
  var right=CorpusFrequencyBuilder.build("en","eligible-v1",List.of("the fox.", "The fox"),0.5);
  assertEquals(4,left.tokenCount()); assertEquals(2,left.vocabularySize()); assertEquals(left.sha256(),right.sha256());
  assertTrue(CorpusFrequencyBuilder.toModel(left).surprisal("missing") > 0);
 }
 @Test void rejectsUnsupportedOrEmptyCorpus(){
  assertThrows(IllegalArgumentException.class,()->CorpusFrequencyBuilder.build("fr","x",List.of("texte"),1));
  assertThrows(IllegalArgumentException.class,()->CorpusFrequencyBuilder.build("pt","x",List.of(""),1));
 }
}
