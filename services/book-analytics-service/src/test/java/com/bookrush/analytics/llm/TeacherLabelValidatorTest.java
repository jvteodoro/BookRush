package com.bookrush.analytics.llm;
import static org.junit.jupiter.api.Assertions.*; import java.util.Map; import org.junit.jupiter.api.Test;
class TeacherLabelValidatorTest { @Test void validatesBoundedKnownLabels(){ assertEquals(.8,TeacherLabelValidator.validate(Map.of("curiosity_gap",.8)).get("curiosity_gap")); assertThrows(IllegalArgumentException.class,()->TeacherLabelValidator.validate(Map.of("unknown",.5))); assertThrows(IllegalArgumentException.class,()->TeacherLabelValidator.validate(Map.of("spoiler_risk",1.1))); } }
