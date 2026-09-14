package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class SemanticPrototypeTest { @Test void scoreIsVersionedAndDimensionSafe(){ var p=new SemanticPrototype("romance","v1",new float[]{1,0}); assertEquals(1,p.cosine(new float[]{1,0}),1e-6); assertThrows(IllegalArgumentException.class,()->p.cosine(new float[]{1})); } }
