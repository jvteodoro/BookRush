package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class FeatureAggregatorTest {
 @Test void ignoresMissingValuesInsteadOfTreatingThemAsZero() {
  var result=FeatureAggregator.mean(List.of(Map.of("x",2d), Map.of("x",4d,"y",1d), Map.of("x",Double.NaN)));
  assertEquals(3d,result.get("x")); assertEquals(1d,result.get("y"));
 }
}
