package com.bookrush.family;

import com.bookrush.platform.core.CorrelationId;
import com.bookrush.platform.core.TechnicalIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ProductSessionController {
  record Request(@Valid @NotNull FamilyController.MembershipQuery actor,
                 @NotNull Boolean invitationScope, @NotNull String correlationId) {}

  private final ProductAccessEvaluator evaluator;
  private final Clock clock;
  private final String issuer;

  ProductSessionController(ProductAccessEvaluator evaluator, Clock clock,
      @Value("${bookrush.security.issuer}") String issuer) {
    this.evaluator = evaluator;
    this.clock = clock;
    this.issuer = issuer;
  }

  @PostMapping("/api/internal/v1/product-access/session")
  ProductAccessEvaluator.Session session(@Valid @RequestBody Request request) {
    try {
      if (!issuer.equals(request.actor().issuer())) throw new IllegalArgumentException();
      var actor = new TechnicalIdentity(request.actor().issuer(), request.actor().subject());
      return evaluator.session(actor, request.invitationScope(), new CorrelationId(request.correlationId()).value(), clock);
    } catch (IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    } catch (DataAccessException | TransactionException failure) {
      throw FamilyProblem.unavailable();
    }
  }
}
