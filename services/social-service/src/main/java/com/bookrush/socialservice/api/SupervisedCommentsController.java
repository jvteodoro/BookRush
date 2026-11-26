package com.bookrush.socialservice.api;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessAuthorization;
import com.bookrush.platform.security.ProductAccessAuthorization.SupervisionScope;
import com.bookrush.platform.security.SupervisedProjection;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableScheduling
@ConditionalOnProperty(name = "bookrush.social.supervision-enabled", havingValue = "true")
class SupervisedCommentsController {
  record Comment(UUID id, UUID bookId, String body, Instant createdAt) {}
  private final JdbcTemplate db;
  private final SupervisedProjection projection;

  SupervisedCommentsController(JdbcTemplate db, ProductAccessAuthorization access, MeterRegistry metrics,
      @Value("${bookrush.security.product-access.jwk-set-uri}") String jwks,
      @Value("${bookrush.security.product-access.workload-issuer}") String issuer) {
    this.db = db;
    projection = new SupervisedProjection(db, access, metrics, SupervisedProjection.Owner.SOCIAL, jwks, issuer);
  }

  @GetMapping("/api/internal/v1/social/supervision/{memberId}/comments")
  ResponseEntity<SupervisedProjection.Page> comments(@PathVariable UUID memberId,
      @RequestParam(defaultValue = "25") int limit, @RequestParam(required = false) UUID cursor,
      @RequestHeader(value = SupervisedProjection.WORKLOAD_HEADER, required = false) String credential,
      JwtAuthenticationToken guardian, HttpServletRequest request) {
    return projection.read(request, guardian, credential, memberId, SupervisionScope.COMMENTS, limit, cursor,
        this::comments);
  }

  private List<SupervisedProjection.Row> comments(TechnicalIdentity target, OffsetDateTime from,
      SupervisedProjection.Position position, int limit) {
    // The product JWT converter uses the issuer-validated subject as the legacy social key.
    return db.query("""
        SELECT c.id,c.book_id,c.body,c.created_at FROM social.comments c
        LEFT JOIN social.comment_moderation_state m ON m.comment_id=c.id
        WHERE c.subject_key=? AND c.created_at>=? AND c.created_at<? AND (c.created_at,c.id)<(?,?)
          AND
        """ + SocialController.COMMENT_AVAILABLE + " ORDER BY c.created_at DESC,c.id DESC LIMIT ?",
        (rs, index) -> {
          var at = rs.getObject("created_at", OffsetDateTime.class);
          var id = rs.getObject("id", UUID.class);
          return new SupervisedProjection.Row(at, id, SupervisedProjection.NIL,
              new Comment(id, rs.getObject("book_id", UUID.class), rs.getString("body"), at.toInstant()));
        }, target.subject(), from, position.upper(), position.time(), position.key(), limit);
  }

  @Scheduled(fixedDelay = 3_600_000)
  void prune() {
    projection.prune();
  }
}
