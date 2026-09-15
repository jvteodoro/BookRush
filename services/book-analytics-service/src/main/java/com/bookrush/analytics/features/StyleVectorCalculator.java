package com.bookrush.analytics.features;

import java.util.*;

/** Standardizes an ordered primitive style vector per language/corpus model. */
public final class StyleVectorCalculator {
  private StyleVectorCalculator() {}
  public record Normalization(String language,String corpusSnapshot,List<String> featureOrder,double[] means,double[] stdDeviations){public Normalization{if(featureOrder.size()!=means.length||means.length!=stdDeviations.length)throw new IllegalArgumentException("normalization dimensions differ");}}
  public static float[] calculate(Map<String,Double> raw, Normalization model){float[] out=new float[model.featureOrder().size()]; for(int i=0;i<out.length;i++){Double x=raw.get(model.featureOrder().get(i)); if(x==null||!Double.isFinite(x))throw new IllegalArgumentException("missing style feature "+model.featureOrder().get(i)); double sd=model.stdDeviations()[i]; out[i]=(float)(sd==0?0:(x-model.means()[i])/sd);} return out;}
}
