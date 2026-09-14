package com.bookrush.analytics.features;

import java.util.*;

/** Offline topic candidate assignment to the closest versioned prototype. */
public final class PrototypeClassifier {
  private PrototypeClassifier() {}
  public static Result classify(float[] vector, List<SemanticPrototype> prototypes) {
    if (prototypes.isEmpty()) return new Result(null,0,null);
    var best=prototypes.stream().map(p -> Map.entry(p,p.cosine(vector))).max(Map.Entry.comparingByValue()).orElseThrow();
    return new Result(best.getKey().code(),best.getValue(),best.getKey().version());
  }
  public record Result(String topic,double score,String prototypeVersion) {}
}
