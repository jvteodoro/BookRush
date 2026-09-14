package com.bookrush.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookrush.ingestion.source.WikidataEnrichment;
import com.bookrush.ingestion.source.WikidataSnapshotReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WikidataSnapshotReaderTest {
  @Test
  void verifiesChecksumAndExtractsOnlyApprovedFields() throws Exception {
    var file = Files.createTempFile("wikidata-", ".jsonl");
    var line = "{\"qid\":\"Q42\",\"claims\":{\"P569\":{\"value\":\"1952-03-11\"},\"P407\":{\"qid\":\"Q1860\"},\"P31\":{\"value\":\"ignored\"}}}";
    Files.writeString(file, line + "\n", StandardCharsets.UTF_8);
    var sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    var snapshot = new WikidataSnapshotReader(new ObjectMapper()).read(file, sha, 10, 4096);
    var fields = WikidataEnrichment.candidates(snapshot.records().getFirst(), snapshot.sha256());
    assertThat(fields).extracting(WikidataEnrichment.FieldCandidate::field).containsExactly("birth_date", "original_language_qid");
    assertThat(fields.getFirst().evidenceSha256()).isEqualTo(sha);
  }

  @Test
  void rejectsChecksumAndInvalidQid() throws Exception {
    var file = Files.createTempFile("wikidata-", ".jsonl");
    Files.writeString(file, "{\"qid\":\"not-qid\",\"claims\":{}}\n");
    assertThatThrownBy(() -> new WikidataSnapshotReader(new ObjectMapper()).read(file, "00", 10, 4096))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("checksum");
  }
}
