package com.bookrush.analytics.excerpts;

import java.util.*;

/** Versioned cheap pruning with an auditable reason for every rejection. */
public final class CandidatePruner {
  private CandidatePruner() {}
  public static Result prune(List<ExcerptCandidateGenerator.Candidate> candidates, Config config) {
    var accepted=new ArrayList<ExcerptCandidateGenerator.Candidate>(); var rejected=new ArrayList<Rejected>(); var seen=new HashSet<String>();
    for (var c:candidates) {
      String reason=null;
      if (c.wordCount()<config.minWords()) reason="TOO_SHORT";
      else if (c.wordCount()>config.maxWords()) reason="TOO_LONG";
      else if (c.text().isBlank()) reason="EMPTY";
      else if (!seen.add(normalize(c.text()))) reason="DUPLICATE";
      if (reason==null) accepted.add(c); else rejected.add(new Rejected(c,reason));
    }
    return new Result(List.copyOf(accepted),List.copyOf(rejected),config.version());
  }
  private static String normalize(String v){return v.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
  public record Config(int minWords,int maxWords,String version){public Config{if(minWords<0||maxWords<minWords)throw new IllegalArgumentException("invalid pruning bounds");}}
  public record Rejected(ExcerptCandidateGenerator.Candidate candidate,String reason){}
  public record Result(List<ExcerptCandidateGenerator.Candidate> accepted,List<Rejected> rejected,String configVersion){}
}
