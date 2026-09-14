package com.bookrush.analytics.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

/** Reproducible JSONL row for teacher/student datasets. */
public record TeacherDatasetRow(String excerptId, String textSha256, String label, double value, double confidence, String teacherModel, String promptVersion, Instant labeledAt) {
  public String toJson(ObjectMapper mapper) throws Exception { return mapper.writeValueAsString(Map.of("excerpt_id",excerptId,"text_sha256",textSha256,"label",label,"value",value,"confidence",confidence,"teacher_model",teacherModel,"prompt_version",promptVersion,"labeled_at",labeledAt.toString())); }
}
