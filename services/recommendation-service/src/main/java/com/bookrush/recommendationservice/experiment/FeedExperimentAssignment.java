package com.bookrush.recommendationservice.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persists a stable control/treatment assignment for an explicitly enabled feed experiment. */
@Component
public class FeedExperimentAssignment {
  private final JdbcTemplate jdbc;
  private final Properties properties;

  public FeedExperimentAssignment(JdbcTemplate jdbc, Properties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  public Assignment assign(String subjectKey) {
    if (!properties.enabled() || subjectKey == null || subjectKey.isBlank()) {
      return Assignment.disabled();
    }
    String experimentId = properties.id();
    String assignmentVersion = properties.assignmentVersion();
    String variant = existing(experimentId, subjectKey);
    if (variant == null) {
      variant = bucket(subjectKey, experimentId) < properties.treatmentPercentage()
          ? "TREATMENT" : "CONTROL";
      jdbc.update("""
          INSERT INTO recommendation.experiment_assignment(
              experiment_id, subject_key, variant, assignment_version)
          VALUES (?, ?, ?, ?)
          ON CONFLICT (experiment_id, subject_key) DO NOTHING
          """, experimentId, subjectKey, variant, assignmentVersion);
      variant = existing(experimentId, subjectKey);
    }
    return new Assignment(experimentId, variant == null ? "CONTROL" : variant,
        assignmentVersion, true);
  }

  private String existing(String experimentId, String subjectKey) {
    var rows = jdbc.queryForList("""
        SELECT variant FROM recommendation.experiment_assignment
        WHERE experiment_id = ? AND subject_key = ?
        """, experimentId, subjectKey);
    return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("variant"));
  }

  private int bucket(String subjectKey, String experimentId) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((experimentId + ":" + subjectKey).getBytes(StandardCharsets.UTF_8));
      long value = Long.parseLong(HexFormat.of().formatHex(digest, 0, 4), 16);
      return (int) (value % 10000);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is required for experiment assignment", exception);
    }
  }

  public record Assignment(String experimentId, String variant, String assignmentVersion,
      boolean assigned) {
    public static Assignment disabled() {
      return new Assignment("", "NOT_ASSIGNED", "", false);
    }

    public boolean control() { return assigned && "CONTROL".equals(variant); }
  }

  @ConfigurationProperties(prefix = "feed.experiment")
  public static class Properties {
    private boolean enabled;
    private String id = "pfv1-personalized-v1";
    private String assignmentVersion = "assignment-v1";
    private int treatmentPercentage = 5000;

    public boolean enabled() { return enabled; }
    public String id() { return id; }
    public String assignmentVersion() { return assignmentVersion; }
    public int treatmentPercentage() { return treatmentPercentage; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setId(String id) { this.id = id == null || id.isBlank() ? "pfv1-personalized-v1" : id; }
    public void setAssignmentVersion(String value) {
      this.assignmentVersion = value == null || value.isBlank() ? "assignment-v1" : value;
    }
    public void setTreatmentPercentage(int value) {
      if (value < 0 || value > 100) throw new IllegalArgumentException("treatment percentage must be 0..100");
      this.treatmentPercentage = value * 100;
    }
  }
}
