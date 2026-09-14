package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class EmbeddingIdentityTest { @Test void hashIsStableAndModelIdentityExplicit(){ var h=EmbeddingIdentity.inputHash("texto😀"); assertEquals(h,EmbeddingIdentity.inputHash("texto😀")); assertThrows(IllegalArgumentException.class,()->new EmbeddingIdentity("m","1",3,"0")); } }
