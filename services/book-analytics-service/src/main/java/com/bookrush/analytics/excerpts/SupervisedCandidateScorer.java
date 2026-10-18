package com.bookrush.analytics.excerpts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Offline adapter for the reproducible candidate scorer artifact. */
public final class SupervisedCandidateScorer {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private final String code;
  private final String version;
  private final String artifactSha256;
  private final Map<String, Model> models;

  private SupervisedCandidateScorer(
      String code, String version, String artifactSha256, Map<String, Model> models) {
    this.code = code;
    this.version = version;
    this.artifactSha256 = artifactSha256;
    this.models = Map.copyOf(models);
  }

  public static SupervisedCandidateScorer load(Path artifact, boolean allowExperimental) {
    Objects.requireNonNull(artifact, "artifact");
    try {
      JsonNode root = MAPPER.readTree(Files.readString(artifact));
      if (!"EXCERPT_SUPERVISED_SCORER_V1".equals(root.path("code").asText()))
        throw new IllegalArgumentException("unsupported scorer artifact");
      String status = root.path("status").asText();
      if (!allowExperimental && !"VALIDATED".equals(status))
        throw new IllegalArgumentException("scorer artifact is not validated: " + status);
      String expected = root.path("artifact_sha256").asText();
      if (expected.length() != 64 || !expected.equals(sha256WithoutDigest(root)))
        throw new IllegalArgumentException("scorer artifact checksum mismatch");
      var models = new LinkedHashMap<String, Model>();
      root.path("models").fields().forEachRemaining(entry -> models.put(entry.getKey(), Model.from(entry.getValue())));
      if (models.isEmpty()) throw new IllegalArgumentException("scorer artifact has no models");
      return new SupervisedCandidateScorer(
          root.path("code").asText(), root.path("version").asText(), expected, models);
    } catch (IOException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("cannot read scorer artifact", e);
    }
  }

  public Result score(Map<String, Double> features) {
    var dimensions = new LinkedHashMap<String, CandidateDimensionSnapshot.Dimension>();
    for (Map.Entry<String, Model> entry : models.entrySet()) {
      String dimension = dimensionName(entry.getKey());
      double value = clamp(entry.getValue().predict(features));
      dimensions.put(
          dimension,
          new CandidateDimensionSnapshot.Dimension(value, "VALID", "supervised." + entry.getKey()));
    }
    double score = dimensions.values().stream().mapToDouble(d -> d.value() == null ? 0 : d.value()).average().orElse(0);
    return new Result(code, version, artifactSha256, score, dimensions);
  }

  private static String dimensionName(String label) {
    return switch (label) {
      case "hook" -> "hook";
      case "curiosity" -> "curiosity";
      case "autonomy" -> "autonomy";
      case "intensity" -> "intensity";
      case "presentation_quality" -> "presentation_quality";
      default -> throw new IllegalArgumentException("unsupported scorer label: " + label);
    };
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }

  private static String sha256WithoutDigest(JsonNode root)
      throws IOException, java.security.NoSuchAlgorithmException {
    ObjectNode copy = (ObjectNode) root.deepCopy();
    copy.remove("artifact_sha256");
    byte[] canonical = MAPPER.writeValueAsBytes(copy);
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
  }

  public record Result(
      String code,
      String version,
      String artifactSha256,
      double score,
      Map<String, CandidateDimensionSnapshot.Dimension> dimensions) {}

  private record Model(double baseline, double learningRate, java.util.List<Tree> trees) {
    static Model from(JsonNode node) {
      JsonNode gbdt = node.path("gbdt");
      var trees = new java.util.ArrayList<Tree>();
      gbdt.path("trees").forEach(t -> trees.add(new Tree(
          t.path("feature").asText(), t.path("threshold").asDouble(),
          t.path("left_value").asDouble(), t.path("right_value").asDouble())));
      return new Model(gbdt.path("baseline").asDouble(), gbdt.path("learning_rate").asDouble(), trees);
    }

    double predict(Map<String, Double> features) {
      double value = baseline;
      for (Tree tree : trees) {
        double feature = features.getOrDefault(tree.feature(), 0d);
        value += learningRate * (feature <= tree.threshold() ? tree.leftValue() : tree.rightValue());
      }
      return value;
    }
  }

  private record Tree(String feature, double threshold, double leftValue, double rightValue) {}
}
