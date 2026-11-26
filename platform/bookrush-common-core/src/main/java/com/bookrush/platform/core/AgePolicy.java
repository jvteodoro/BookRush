package com.bookrush.platform.core;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Civil age derived from identity-owned facts, independently of token age. */
public final class AgePolicy {
  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final Pattern ISO_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

  public enum State { MISSING, INVALID, MINOR, ADULT }
  public enum Provenance { NONE, DECLARED, VERIFIED }

  public record Decision(State state, Provenance provenance, Instant adultFrom) {
    public boolean meetsEvidenceRequirement(boolean verificationRequired) {
      return (state == State.MINOR || state == State.ADULT)
          && (!verificationRequired || provenance == Provenance.VERIFIED);
    }
  }

  /** Adapter accepts only an authenticated product identity and reads current facts from its owner. */
  @FunctionalInterface
  public interface Source {
    Decision classify(TechnicalIdentity identity);
  }

  private AgePolicy() {}

  public static Optional<LocalDate> validDate(String value, Clock clock) {
    if (value == null || !ISO_DATE.matcher(value).matches()) {
      return Optional.empty();
    }
    try {
      LocalDate date = LocalDate.parse(value);
      return date.getYear() > 0 && !date.isAfter(LocalDate.now(clock.withZone(ZONE)))
          ? Optional.of(date) : Optional.empty();
    } catch (DateTimeException invalid) {
      return Optional.empty();
    }
  }

  public static Decision classify(List<String> dates, Provenance provenance, Clock clock) {
    if (dates.isEmpty()) {
      return new Decision(State.MISSING, Provenance.NONE, null);
    }
    if (dates.size() != 1 || provenance == Provenance.NONE) {
      return new Decision(State.INVALID, Provenance.NONE, null);
    }
    return validDate(dates.getFirst(), clock)
        .map(date -> {
          int year = date.getYear() + 18;
          LocalDate birthday = date.getMonth() == Month.FEBRUARY && date.getDayOfMonth() == 29
              && !Year.isLeap(year) ? LocalDate.of(year, Month.MARCH, 1) : date.plusYears(18);
          Instant cutoff = birthday.atStartOfDay(ZONE).toInstant();
          return new Decision(clock.instant().isBefore(cutoff) ? State.MINOR : State.ADULT, provenance, cutoff);
        })
        .orElseGet(() -> new Decision(State.INVALID, Provenance.NONE, null));
  }
}
