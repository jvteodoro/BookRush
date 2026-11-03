package com.bookrush.analytics.excerpts;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisedCandidateScorerTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  @Test
  void loadsChecksumAndScoresSmallBatchWithoutNetwork() throws Exception {
    Path artifact = Files.createTempFile("candidate-scorer", ".json");
    try {
      var root = MAPPER.createObjectNode();
      root.put("code", "EXCERPT_SUPERVISED_SCORER_V1");
      root.put("version", "1.1.0");
      root.put("status", "VALIDATED");
      var models = root.putObject("models");
      var hook = models.putObject("hook").putObject("gbdt");
      hook.put("baseline", 0.2).put("learning_rate", 0.1);
      hook.putArray("trees").addObject().put("feature", "narrative.hook")
          .put("threshold", 0.5).put("left_value", -0.1).put("right_value", 0.2);
      var curiosity = models.putObject("curiosity").putObject("gbdt");
      curiosity.put("baseline", 0.4).put("learning_rate", 0.1);
      curiosity.putArray("trees");
      root.put("artifact_sha256", digest(root));
      Files.writeString(artifact, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));

      var scorer = SupervisedCandidateScorer.load(artifact, false);
      var result = scorer.score(Map.of("narrative.hook", 0.8));

      assertEquals("EXCERPT_SUPERVISED_SCORER_V1", result.code());
      assertEquals(2, result.dimensions().size());
      assertEquals("VALID", result.dimensions().get("hook").status());
      assertTrue(result.score() > 0.0 && result.score() <= 1.0);
    } finally {
      Files.deleteIfExists(artifact);
    }
  }

  @Test
  void rejectsExperimentalArtifactUnlessExplicitlyEnabled() throws Exception {
    Path artifact = Files.createTempFile("candidate-scorer", ".json");
    try {
      var root = MAPPER.createObjectNode();
      root.put("code", "EXCERPT_SUPERVISED_SCORER_V1");
      root.put("version", "1.1.0");
      root.put("status", "EXPERIMENTAL");
      root.putObject("models").putObject("hook").putObject("gbdt")
          .put("baseline", 0.5).put("learning_rate", 0.1).putArray("trees");
      root.put("artifact_sha256", digest(root));
      Files.writeString(artifact, MAPPER.writeValueAsString(root));

      assertThrows(IllegalArgumentException.class, () -> SupervisedCandidateScorer.load(artifact, false));
      assertDoesNotThrow(() -> SupervisedCandidateScorer.load(artifact, true));
    } finally {
      Files.deleteIfExists(artifact);
    }
  }

  private static String digest(com.fasterxml.jackson.databind.JsonNode root) throws Exception {
    com.fasterxml.jackson.databind.node.ObjectNode copy =
        (com.fasterxml.jackson.databind.node.ObjectNode) root.deepCopy();
    copy.remove("artifact_sha256");
    return java.util.HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(copy)));
  }
}
