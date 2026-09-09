package com.bookrush.catalog.canonical;

import java.util.Map;
import java.util.Objects;

/** Deterministic precedence used when source metadata is applied to canonical fields. */
public final class FieldPolicy {
  public enum Authority { CURATED, SOURCE, UNKNOWN }

  public Decision decide(Authority currentAuthority, boolean curatedLock, Authority incomingAuthority,
                         boolean incomingPresent, boolean explicitDeletion) {
    if (curatedLock || !incomingPresent) return new Decision(false, "CURATED_LOCK_OR_MISSING");
    if (explicitDeletion && incomingAuthority != Authority.CURATED) return new Decision(false, "DELETION_REQUIRES_CURATED_EVIDENCE");
    if (currentAuthority == Authority.CURATED && incomingAuthority != Authority.CURATED) return new Decision(false, "CURATED_WINS");
    if (incomingAuthority == Authority.UNKNOWN) return new Decision(false, "NO_AUTHORITY");
    return new Decision(true, incomingAuthority == currentAuthority ? "SAME_AUTHORITY_REFRESH" : "HIGHER_OR_EQUAL_AUTHORITY");
  }

  public Map<String, Object> provenance(String field, String sourceCode, String sourceRevision,
                                        String oldValue, String newValue, String rule, String configHash) {
    return Map.of("field", Objects.requireNonNull(field), "sourceCode", sourceCode,
        "sourceRevision", sourceRevision, "oldValue", oldValue, "newValue", newValue,
        "rule", rule, "configHash", configHash);
  }

  public record Decision(boolean apply, String reason) {}
}
