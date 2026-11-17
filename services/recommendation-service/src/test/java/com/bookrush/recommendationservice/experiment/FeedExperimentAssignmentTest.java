package com.bookrush.recommendationservice.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FeedExperimentAssignmentTest {
  @Test
  void disabledExperimentDoesNotTouchTheLedger() {
    var jdbc = mock(JdbcTemplate.class);
    var properties = new FeedExperimentAssignment.Properties();
    var service = new FeedExperimentAssignment(jdbc, properties);

    var assignment = service.assign("subject-1");

    assertFalse(assignment.assigned());
    assertEquals("NOT_ASSIGNED", assignment.variant());
    verify(jdbc, org.mockito.Mockito.never()).queryForList(anyString(), any(Object[].class));
  }

  @Test
  void assignmentIsStableAndPersistsTheSelectedVariant() {
    var jdbc = mock(JdbcTemplate.class);
    var properties = new FeedExperimentAssignment.Properties();
    properties.setEnabled(true);
    properties.setId("experiment-test");
    properties.setTreatmentPercentage(100);
    var service = new FeedExperimentAssignment(jdbc, properties);
    when(jdbc.queryForList(anyString(), eq("experiment-test"), eq("subject-1")))
        .thenReturn(List.of(), List.of(Map.of("variant", "TREATMENT")));

    var assignment = service.assign("subject-1");

    assertTrue(assignment.assigned());
    assertEquals("TREATMENT", assignment.variant());
    verify(jdbc).update(anyString(), eq("experiment-test"), eq("subject-1"),
        eq("TREATMENT"), eq("assignment-v1"));
  }
}
