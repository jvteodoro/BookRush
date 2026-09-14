package com.bookrush.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookrush.ingestion.source.OpenLibrarySubjectExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenLibrarySubjectExtractorTest {
  @Test
  void extractsTextAndNamedSubjectsWithoutDuplicates() throws Exception {
    var payload = new ObjectMapper().readTree("{\"subjects\":[\"Love stories\",{\"name\":\"Love stories\"},{\"name\":\" Fiction \"}]} ");
    assertThat(OpenLibrarySubjectExtractor.extract(payload)).containsExactly("Love stories", "Fiction");
  }

  @Test
  void unsupportedShapeIsEmpty() throws Exception {
    assertThat(OpenLibrarySubjectExtractor.extract(new ObjectMapper().readTree("{\"subjects\":null}"))).isEmpty();
  }
}
