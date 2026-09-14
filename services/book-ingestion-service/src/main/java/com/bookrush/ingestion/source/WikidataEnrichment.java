package com.bookrush.ingestion.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Converts an approved snapshot record into conservative, auditable field candidates. */
public final class WikidataEnrichment {
  private WikidataEnrichment() {}

  public static List<FieldCandidate> candidates(WikidataSnapshotReader.Record record, String evidenceSha256) {
    var result = new ArrayList<FieldCandidate>();
    addString(result, record, "P569", "birth_date", evidenceSha256, record.claims().path("P569").path("value").asText(null), "HIGH");
    addString(result, record, "P570", "death_date", evidenceSha256, record.claims().path("P570").path("value").asText(null), "HIGH");
    addString(result, record, "P407", "original_language_qid", evidenceSha256, record.claims().path("P407").path("qid").asText(null), "MEDIUM");
    addString(result, record, "P135", "literary_movement_qid", evidenceSha256, record.claims().path("P135").path("qid").asText(null), "MEDIUM");
    addString(result, record, "P496", "orcid", evidenceSha256, record.claims().path("P496").path("value").asText(null), "HIGH");
    addString(result, record, "P214", "viaf", evidenceSha256, record.claims().path("P214").path("value").asText(null), "HIGH");
    return List.copyOf(result);
  }

  private static void addString(List<FieldCandidate> result, WikidataSnapshotReader.Record record,
      String property, String field, String evidence, String value, String confidence) {
    if (value != null && !value.isBlank()) result.add(new FieldCandidate(record.qid(), property, field, value, confidence, evidence, record.sourceLine()));
  }

  public record FieldCandidate(String qid, String property, String field, String value,
      String confidence, String evidenceSha256, long sourceLine) {}
}
