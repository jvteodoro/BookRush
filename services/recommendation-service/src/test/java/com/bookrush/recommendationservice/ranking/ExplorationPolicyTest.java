package com.bookrush.recommendationservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplorationPolicyTest {
  @Test
  void sameSeedAndConfigProduceTheSameBoundedChoices() {
    var policy = new ExplorationPolicy(new ExplorationPolicy.Config("explore-v1", true, .25, 2));
    var ids = List.of("a", "b", "c", "d", "e", "f");
    var first = policy.choose(ids, Set.of("a"), 77, 4);
    var second = policy.choose(ids, Set.of("a"), 77, 4);
    assertEquals(first, second);
    assertTrue(first.size() <= 2);
    assertTrue(first.stream().noneMatch(choice -> choice.bookId().equals("a")));
    assertTrue(first.stream().allMatch(choice -> choice.reason().equals("epsilon-exploration")));
  }

  @Test
  void disabledExplorationHasNoChoicesAndLargeSimulationRespectsQuota() {
    var disabled = new ExplorationPolicy(new ExplorationPolicy.Config("off", false, .5, 10));
    assertTrue(disabled.choose(List.of("a", "b"), Set.of(), 1, 10).isEmpty());

    var policy = new ExplorationPolicy(new ExplorationPolicy.Config("explore-v1", true, .2, 3));
    int total = 0;
    for (int seed = 0; seed < 200; seed++) {
      total += policy.choose(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
          Set.of(), seed, 10).size();
    }
    assertTrue(total <= 600);
    assertTrue(total >= 100, "exploration should remain observable over the sample");
  }
}
