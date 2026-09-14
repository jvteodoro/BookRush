package com.bookrush.analytics.features;
import static org.junit.jupiter.api.Assertions.*; import java.util.List; import org.junit.jupiter.api.Test;
class DisabledEmbeddingProviderTest { @Test void batchDoesNotDownloadOrInventVectors(){ var r=new DisabledEmbeddingProvider().embed(List.of("a","b")); assertFalse(r.supported()); assertTrue(r.vectors().isEmpty()); } }
