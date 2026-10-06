package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StructuredContentEligibilityTest {
  @Test
  void rejectsFrankensteinContentsButAcceptsChapterProse() {
    String contents = "CONTENTS\nLetter 1........1\nLetter 2........4\nChapter 5........20\nChapter 24........200";
    assertEquals("TABLE_OF_CONTENTS", StructuredContentEligibility.classify(contents).exclusionReason());

    String prose = "Chapter 5\n\nI was deeply interested in the sequence of events that followed. "
        + "The room was quiet, and every small sound made the scene more vivid.";
    assertTrue(StructuredContentEligibility.classify(prose).bodyEligible());
  }
}
