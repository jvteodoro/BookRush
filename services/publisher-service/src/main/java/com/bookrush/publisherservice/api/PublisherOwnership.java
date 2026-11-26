package com.bookrush.publisherservice.api;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessRoutes;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PublisherOwnership {
  static final String OWN = "identity_issuer=? AND identity_subject=?";
  static final Set<String> EDITABLE = Set.of("DRAFT", "UPLOADED", "CHANGES_REQUESTED");

  record Submission(UUID id, String legacySubject, String title, String status, UUID catalogBookId, boolean catalogLinked,
                    TechnicalIdentity author) {
    void requireEditable() {
      if (!EDITABLE.contains(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "submission is not editable");
    }
  }

  private final JdbcTemplate db;

  PublisherOwnership(JdbcTemplate db) {
    this.db = db;
  }

  Submission lock(UUID id, TechnicalIdentity actor) {
    return db.query("SELECT * FROM publisher.submission WHERE id=? AND " + OWN + " FOR UPDATE",
        rs -> { return row(id, rs); }, id, actor.issuer(), actor.subject());
  }

  Submission lockForReview(UUID id) {
    return db.query("SELECT * FROM publisher.submission WHERE id=? FOR UPDATE", rs -> { return row(id, rs); }, id);
  }

  /** Authoritative resource for the next Bead's PUBLISH_* gates. Mutations still lock and recheck. */
  public ProductAccessRoutes.Resource resource(UUID id, TechnicalIdentity actor) {
    var owner = db.query("SELECT identity_issuer,identity_subject FROM publisher.submission WHERE id=? AND " + OWN,
        rs -> {
          if (!rs.next()) throw missing();
          return new TechnicalIdentity(rs.getString(1), rs.getString(2));
        }, id, actor.issuer(), actor.subject());
    return new ProductAccessRoutes.Resource(ProductAccessRoutes.Type.SUBMISSION, id.toString(), owner);
  }

  private static Submission row(UUID id, java.sql.ResultSet rs) throws java.sql.SQLException {
    if (!rs.next()) throw missing();
    String issuer = rs.getString("identity_issuer");
    return new Submission(id, rs.getString("subject_key"), rs.getString("title"), rs.getString("status"),
        rs.getObject("catalog_book_id", UUID.class), rs.getObject("linked_at") != null,
        issuer == null ? null : new TechnicalIdentity(issuer, Objects.requireNonNull(rs.getString("identity_subject"))));
  }

  static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
  }
}
