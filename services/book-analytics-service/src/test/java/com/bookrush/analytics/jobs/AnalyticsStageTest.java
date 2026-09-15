package com.bookrush.analytics.jobs;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class AnalyticsStageTest { @Test void stagesAreOrderedAndTerminal(){assertEquals(AnalyticsStage.STRUCTURAL,AnalyticsStage.VALIDATE_LANGUAGE.next().orElseThrow()); assertTrue(AnalyticsStage.STRUCTURAL.precedes(AnalyticsStage.NARRATIVE_NLI)); assertTrue(AnalyticsStage.DONE.next().isEmpty());} }
