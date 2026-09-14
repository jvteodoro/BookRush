package com.bookrush.analytics.llm;
import static org.junit.jupiter.api.Assertions.*; import com.fasterxml.jackson.databind.ObjectMapper; import java.time.Instant; import org.junit.jupiter.api.Test;
class TeacherDatasetRowTest { @Test void serializesStableDatasetContract() throws Exception { var json=new TeacherDatasetRow("e","a".repeat(64),"curiosity_gap",.8,.9,"model","p1",Instant.parse("2026-01-01T00:00:00Z")).toJson(new ObjectMapper()); assertTrue(json.contains("curiosity_gap")); assertTrue(json.contains("prompt_version")); } }
