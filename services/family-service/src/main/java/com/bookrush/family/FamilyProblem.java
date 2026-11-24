package com.bookrush.family;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class FamilyProblem extends ResponseStatusException {
  private FamilyProblem(HttpStatus status, String code) {
    super(status, code);
    getBody().setProperty("code", code);
  }

  static FamilyProblem unavailable() {
    return new FamilyProblem(HttpStatus.SERVICE_UNAVAILABLE, "DECISION_UNAVAILABLE");
  }

  static FamilyProblem forbidden(String code) {
    return new FamilyProblem(HttpStatus.FORBIDDEN, code);
  }

  static FamilyProblem missing(String code) {
    return new FamilyProblem(HttpStatus.NOT_FOUND, code);
  }

  static FamilyProblem conflict(String code) {
    return new FamilyProblem(HttpStatus.CONFLICT, code);
  }
}
