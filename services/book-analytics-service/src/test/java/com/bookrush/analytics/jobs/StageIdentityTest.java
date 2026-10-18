package com.bookrush.analytics.jobs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageIdentityTest {
  @Test
  void cacheIdentityIncludesOptionalModelInputs() {
    var asset = UUID.randomUUID();
    var base = new StageIdentity(asset, "text", "analyzer", "model", "config", "h1", "p1", "c1");
    var same = new StageIdentity(asset, "text", "analyzer", "model", "config", "h1", "p1", "c1");
    var changedHypothesis = new StageIdentity(asset, "text", "analyzer", "model", "config", "h2", "p1", "c1");
    var changedPrototype = new StageIdentity(asset, "text", "analyzer", "model", "config", "h1", "p2", "c1");
    var changedCorpus = new StageIdentity(asset, "text", "analyzer", "model", "config", "h1", "p1", "c2");

    assertTrue(base.reusableBy(same));
    assertFalse(base.reusableBy(changedHypothesis));
    assertFalse(base.reusableBy(changedPrototype));
    assertFalse(base.reusableBy(changedCorpus));
  }
}
