package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant; import java.util.*;
import org.junit.jupiter.api.Test;
class ExcerptFeatureObservationFactoryTest {
 @Test void observationsAreStableAndCarryRunIdentity(){
  var run=UUID.randomUUID(); var ex=UUID.randomUUID(); var now=Instant.now();
  var values=Map.of("word_count",3d,"question_ratio",.5d);
  var rows=ExcerptFeatureObservationFactory.from(run,ex,UUID.randomUUID(),values,now);
  assertEquals(List.of("question_ratio","word_count"),rows.stream().map(ExcerptFeatureObservationFactory.Observation::featureCode).toList());
  assertTrue(rows.stream().allMatch(x->x.analysisRunId().equals(run)&&x.excerptId().equals(ex)&&x.computedAt().equals(now)));
 }
}
