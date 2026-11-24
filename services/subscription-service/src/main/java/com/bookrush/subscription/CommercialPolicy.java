package com.bookrush.subscription;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CommercialPolicy {
  enum State { FREE, ACTIVE, CANCEL_AT_END, CANCELED, EXPIRED, SUSPENDED }
  enum Operation { ACTIVATE, RENEW, CANCEL_AT_END, CANCEL_NOW, EXPIRE, SUSPEND, REACTIVATE }
  enum Entitlement { PUBLISH_BOOKS, PREMIUM_FAMILY }

  record Contract(String plan, int planVersion, State state, Instant startsAt, Instant endsAt,
                  long version, boolean publishBooks, boolean premiumFamily, int seats) {
    State effectiveState(Instant now) {
      return (state == State.ACTIVE || state == State.CANCEL_AT_END || state == State.SUSPENDED)
          && !now.isBefore(endsAt) ? State.EXPIRED : state;
    }

    boolean paid(Instant now) {
      State current = effectiveState(now);
      return (current == State.ACTIVE || current == State.CANCEL_AT_END) && !now.isBefore(startsAt);
    }
  }

  private CommercialPolicy() {}

  static State next(Contract current, Operation operation, Instant now, Instant start, Instant end) {
    State effective = current.effectiveState(now);
    boolean suppliesWindow = operation == Operation.ACTIVATE || operation == Operation.RENEW;
    if (suppliesWindow) {
      if (start == null || end == null || !start.isBefore(end) || start.isAfter(now) || !end.isAfter(now)) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid effective period");
      }
    } else if (start != null || end != null) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "period is not accepted for this transition");
    }
    boolean allowed = switch (operation) {
      case ACTIVATE -> effective == State.FREE || effective == State.EXPIRED || effective == State.CANCELED;
      case RENEW -> effective == State.ACTIVE && start.equals(current.startsAt()) && end.isAfter(current.endsAt());
      case CANCEL_AT_END -> effective == State.ACTIVE;
      case CANCEL_NOW -> effective == State.ACTIVE || effective == State.CANCEL_AT_END || effective == State.SUSPENDED;
      case EXPIRE -> effective == State.EXPIRED && current.state() != State.EXPIRED;
      case SUSPEND -> effective == State.ACTIVE || effective == State.CANCEL_AT_END;
      case REACTIVATE -> effective == State.SUSPENDED || effective == State.CANCEL_AT_END;
    };
    if (!allowed) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "transition is invalid for current state");
    }
    return switch (operation) {
      case ACTIVATE, RENEW, REACTIVATE -> State.ACTIVE;
      case CANCEL_AT_END -> State.CANCEL_AT_END;
      case CANCEL_NOW -> State.CANCELED;
      case EXPIRE -> State.EXPIRED;
      case SUSPEND -> State.SUSPENDED;
    };
  }
}
