package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateDimensionSnapshotTest {
  @Test
  void preservesValidDimensionsAndDoesNotFabricateMissingLabels() {
    var snapshot =
        CandidateDimensionSnapshot.from(
            Map.of("narrative.hook", 0.8, "narrative.autonomy", 0.4),
            Map.of("narrative.hook", "VALID", "narrative.autonomy", "MODEL_UNAVAILABLE"));

    assertEquals(0.8, snapshot.get("hook").value());
    assertEquals("VALID", snapshot.get("hook").status());
    assertNull(snapshot.get("autonomy").value());
    assertEquals("MODEL_UNAVAILABLE", snapshot.get("autonomy").status());
    assertEquals("UNAVAILABLE", snapshot.get("curiosity").status());
  }
}
