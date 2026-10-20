package com.bookrush.platform.core;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;

/** Age band derived from an ISO-8601 birthdate. Anything unverifiable is UNKNOWN. */
public enum AgeCategory {
  ADULT, MINOR, UNKNOWN;

  public static final int ADULT_AGE = 18;

  public static AgeCategory fromBirthdate(String isoDate, Clock clock) {
    if (isoDate == null || isoDate.isBlank()) return UNKNOWN;
    try {
      LocalDate birth = LocalDate.parse(isoDate.trim());
      LocalDate today = LocalDate.now(clock);
      if (birth.isAfter(today)) return UNKNOWN;
      return Period.between(birth, today).getYears() >= ADULT_AGE ? ADULT : MINOR;
    } catch (DateTimeException invalid) {
      return UNKNOWN;
    }
  }

  /** Restricted content (minAge > 0) requires a verified adult; UNKNOWN fails closed. */
  public boolean canAccess(int minAge) {
    return minAge <= 0 || this == ADULT;
  }
}
