package com.bookrush.analytics.features;

import java.util.List;

public record SemanticPrototype(String code, String version, float[] vector) {
  public SemanticPrototype { if (code==null||version==null||vector==null||vector.length==0) throw new IllegalArgumentException("prototype is incomplete"); vector=EmbeddingMath.l2Normalize(vector); }
  public static SemanticPrototype centroid(String code, String version, List<float[]> examples) {
    if (examples==null||examples.isEmpty()) throw new IllegalArgumentException("prototype examples are required");
    return new SemanticPrototype(code,version,EmbeddingMath.weightedMean(examples, java.util.Collections.nCopies(examples.size(),1)));
  }
  public double cosine(float[] other) { if (other==null||other.length!=vector.length) throw new IllegalArgumentException("dimension mismatch"); double dot=0,a=0,b=0; for(int i=0;i<vector.length;i++){dot+=vector[i]*other[i];a+=vector[i]*vector[i];b+=other[i]*other[i];} return a==0||b==0?0:dot/(Math.sqrt(a)*Math.sqrt(b)); }
}
