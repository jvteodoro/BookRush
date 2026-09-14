package com.bookrush.analytics.llm;

import java.util.*;

/** Validates teacher labels against the bounded contract before persistence. */
public final class TeacherLabelValidator {
  private static final Set<String> LABELS=Set.of("curiosity_gap","self_containment","cliffhanger","spoiler_risk","quotability");
  private TeacherLabelValidator(){}
  public static Map<String,Double> validate(Map<String,Double> labels) {
    var result=new TreeMap<String,Double>();
    for(var e:labels.entrySet()){ if(!LABELS.contains(e.getKey())) throw new IllegalArgumentException("unknown teacher label: "+e.getKey()); if(e.getValue()==null||e.getValue().isNaN()||e.getValue()<0||e.getValue()>1) throw new IllegalArgumentException("teacher label outside [0,1]"); result.put(e.getKey(),e.getValue()); }
    return Map.copyOf(result);
  }
}
