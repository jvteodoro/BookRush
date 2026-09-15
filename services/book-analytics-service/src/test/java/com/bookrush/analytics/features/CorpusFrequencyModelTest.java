package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class CorpusFrequencyModelTest { @Test void computesAddAlphaSurprisalAndPercentile(){var m=new CorpusFrequencyModel("en","fixture",Map.of("common",9L,"rare",1L),10,1); var s=m.summarize(List.of("common","rare","oov")); assertTrue(s.mean()>0); assertEquals(3,s.sampleSize()); assertTrue(m.surprisal("oov")>m.surprisal("common"));} }
