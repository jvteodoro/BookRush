package com.bookrush.analytics.excerpts;
import static org.junit.jupiter.api.Assertions.*; import java.util.Map; import org.junit.jupiter.api.Test;
class ExcerptRankerTest { @Test void isExplainableAndBounded(){ var r=ExcerptRanker.rank(Map.of("question_ratio",.5,"dialogue_ratio",.2),new ExcerptRanker.Weights("rank-v1",Map.of("question_ratio",.6,"dialogue_ratio",.4))); assertEquals(.38,r.score(),1e-9); assertEquals(.3,r.contributions().get("question_ratio")); assertEquals("rank-v1",r.version()); } }
