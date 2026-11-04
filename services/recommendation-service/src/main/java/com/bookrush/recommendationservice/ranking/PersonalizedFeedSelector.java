package com.bookrush.recommendationservice.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composes the PFV1 personalized path without applying a global top-N first.
 * Exploration receives a reserved quota after scoring; MMR selects the
 * exploitation slots from the full personalized pool and exploration choices
 * are inserted deterministically afterwards.
 */
public final class PersonalizedFeedSelector {
  private final PersonalizedRankerV1 ranker;
  private final ExplorationPolicy exploration;
  private final BookDiversityMmr mmr;

  public PersonalizedFeedSelector(PersonalizedRankerV1 ranker, ExplorationPolicy exploration,
      BookDiversityMmr mmr) {
    this.ranker = ranker;
    this.exploration = exploration;
    this.mmr = mmr;
  }

  public Result select(List<PersonalizedRankerV1.Candidate> candidates,
      Map<String, List<Double>> embeddings, int limit, long seed) {
    if (limit < 1) return new Result(List.of(), Map.of(), Map.of(), Set.of());
    var ranked = ranker.rank(candidates, seed);
    int explorationSlots = Math.min(exploration.quota(limit), ranked.size());
    int exploitationSlots = Math.max(0, Math.min(limit - explorationSlots, ranked.size()));
    int poolStart = Math.min(exploitationSlots, ranked.size());
    int explorationPoolSize = Math.max(explorationSlots * 4, explorationSlots);
    var explorationPool = ranked.subList(poolStart, ranked.size()).stream()
        // Keep exploration outside the exploitation prefix, but prefer candidates
        // with higher freshness so recently overexposed books are less likely to
        // consume a reserved discovery slot.
        .sorted(Comparator.comparingDouble(
            (PersonalizedRankerV1.ScoredCandidate candidate) -> candidate.breakdown().freshness())
            .reversed()
            .thenComparing(Comparator.comparingDouble(PersonalizedRankerV1.ScoredCandidate::score)
                .reversed())
            .thenComparing(candidate -> candidate.candidate().id()))
        .limit(explorationPoolSize)
        .map(candidate -> candidate.candidate().id()).toList();
    var choices = exploration.choose(explorationPool, Set.of(), seed, limit);
    var explorationIds = choices.stream().map(ExplorationPolicy.Choice::bookId)
        .collect(Collectors.toUnmodifiableSet());
    var exploitationPool = ranked.stream()
        .filter(candidate -> !explorationIds.contains(candidate.candidate().id())).toList();
    var exploitation = mmr.select(exploitationPool, embeddings, exploitationSlots);
    var byId = ranked.stream().collect(Collectors.toMap(
        candidate -> candidate.candidate().id(), Function.identity(), (left, right) -> left,
        LinkedHashMap::new));
    var selected = new ArrayList<BookDiversityMmr.Selection>();
    selected.addAll(exploitation);
    for (var choice : choices) {
      var candidate = byId.get(choice.bookId());
      if (candidate != null && selected.size() < limit) {
        selected.add(new BookDiversityMmr.Selection(candidate, candidate.score(), 0,
            "exploration-reserved-slot"));
      }
    }
    return new Result(List.copyOf(selected),
        ranked.stream().collect(Collectors.toUnmodifiableMap(
            candidate -> candidate.candidate().id(), Function.identity(), (left, right) -> left)),
        exploitation.stream().collect(Collectors.toUnmodifiableMap(
            selection -> selection.candidate().candidate().id(), Function.identity())),
        explorationIds);
  }

  public record Result(List<BookDiversityMmr.Selection> selections,
      Map<String, PersonalizedRankerV1.ScoredCandidate> scoredById,
      Map<String, BookDiversityMmr.Selection> mmrSelectionsById,
      Set<String> explorationIds) { }
}
