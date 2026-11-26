package com.bookrush.platform.security;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.platform.security.ProductAccessAuthorization.SupervisionScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/** Owner-side authorization, keyset cursor and audit for supervised projections. */
public final class SupervisedProjection {
  public static final UUID NIL = new UUID(0, 0);
  public static final String WORKLOAD_HEADER = "X-BookRush-Workload";
  public static final String BFF_CLIENT = "bookrush-reader-bff-supervision-s2s";

  public enum Owner {
    READER_STATE("reader_state.supervised_reading_", "bookrush-reader-state-supervision", "supervision.history.read"),
    SOCIAL("social.supervised_comments_", "bookrush-social-supervision", "supervision.comments.read");

    final String table;
    final String audience;
    public final String scope;
    Owner(String table, String audience, String scope) {
      this.table = table;
      this.audience = audience;
      this.scope = scope;
    }
  }

  public record Position(OffsetDateTime upper, OffsetDateTime time, UUID key, UUID tie) {}
  public record Row(OffsetDateTime time, UUID key, UUID tie, Object item) {}
  public record Page(SupervisionScope scope, List<?> items, UUID nextCursor) {}

  @FunctionalInterface
  public interface Source {
    List<Row> read(TechnicalIdentity target, OffsetDateTime from, Position position, int limit);
  }

  private final JdbcTemplate db;
  private final ProductAccessAuthorization access;
  private final MeterRegistry metrics;
  private final NimbusJwtDecoder workload;
  private final Owner owner;

  public SupervisedProjection(JdbcTemplate db, ProductAccessAuthorization access,
      MeterRegistry metrics, Owner owner, String jwks, String issuer) {
    this.db = db;
    this.access = access;
    this.metrics = metrics;
    this.owner = owner;
    workload = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    workload.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
  }

  public ResponseEntity<Page> read(HttpServletRequest request, JwtAuthenticationToken guardian, String credential,
      UUID member, SupervisionScope scope, int limit, UUID cursor, Source source) {
    verifyHumanToken(guardian);
    verifyWorkload(credential);
    var jwt = guardian.getToken();
    String actor = new TechnicalIdentity(jwt.getIssuer().toString(), jwt.getSubject()).toString();
    String correlation = ProductAccessAuthorization.supervisionCorrelation(request);
    int outcome = 503;
    var timer = Timer.start(metrics);
    try {
      verifyHumanScope(guardian, limit);
      var window = access.supervisedWindow(request, guardian, member, scope);
      String target = window.minor().toString();
      OffsetDateTime upper = utc(window.until());
      var position = cursor == null ? new Position(upper, upper, NIL, NIL)
          : lookup(cursor, actor, target, member, scope, window.version());
      var rows = source.read(window.minor(), utc(window.from()), position, limit + 1);
      var current = access.supervisedWindow(request, guardian, member, scope);
      if (!current.minor().equals(window.minor()) || !current.version().equals(window.version())
          || !Instant.now().isBefore(current.validUntil())) throw error(403);
      if (current.from().isAfter(window.from())) {
        rows = source.read(current.minor(), utc(current.from()), position, limit + 1);
      }
      if (!Instant.now().isBefore(current.validUntil())) throw error(403);
      UUID next = rows.size() > limit ? save(actor, target, member, scope, current.version(), position,
          rows.get(limit - 1), current.validUntil()) : null;
      outcome = 200;
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .body(new Page(scope, rows.stream().limit(limit).map(Row::item).toList(), next));
    } catch (ResponseStatusException failure) {
      outcome = failure.getStatusCode().value();
      throw failure;
    } catch (DataAccessException failure) {
      throw error(503);
    } finally {
      timer.stop(metrics.timer("bookrush.supervision.latency", "owner", owner.name()));
      metrics.counter("bookrush.supervision.requests", "owner", owner.name(), "outcome", Integer.toString(outcome)).increment();
      try {
        var resolved = ProductAccessAuthorization.supervisedTarget(request);
        db.update("INSERT INTO " + owner.table + "audit(guardian_key,target_key,member_id,scope,outcome,correlation_id)"
            + " VALUES (?,?,?,?,?,?)", actor, resolved == null ? null : resolved.toString(), member, scope.name(), outcome, correlation);
      } catch (DataAccessException failure) {
        throw error(503);
      }
    }
  }

  public static void verifyHumanToken(JwtAuthenticationToken guardian) {
    if (guardian == null || !guardian.isAuthenticated() || guardian.getToken().getSubject() == null
        || guardian.getToken().getSubject().isBlank() || guardian.getToken().getAudience() == null
        || !guardian.getToken().getAudience().contains("bookrush-reader-bff")) throw error(401);
  }

  public static void verifyHumanScope(JwtAuthenticationToken guardian, int limit) {
    var jwt = guardian.getToken();
    String caller = jwt.getClaimAsString("azp");
    if (caller == null || !Set.of("bookrush-web", "bookrush-mobile").contains(caller)
        || !ProductAccessAuthorization.scopes(jwt.getClaims().get("scope")).contains("supervision.read")) throw error(403);
    if (limit < 1 || limit > 50) throw error(400);
  }

  private void verifyWorkload(String credential) {
    if (credential == null || credential.isBlank()) throw error(401);
    try {
      var token = workload.decode(credential);
      if (token.getExpiresAt() == null || !Instant.now().isBefore(token.getExpiresAt())
          || token.getAudience() == null || !token.getAudience().contains(owner.audience)) throw error(401);
      if (!BFF_CLIENT.equals(token.getClaimAsString("azp"))
          || !ProductAccessAuthorization.scopes(token.getClaims().get("scope")).contains(owner.scope)) throw error(403);
    } catch (BadJwtException failure) {
      throw error(401);
    } catch (JwtException failure) {
      throw error(503);
    }
  }

  private Position lookup(UUID id, String actor, String target, UUID member, SupervisionScope scope, long version) {
    return db.query("SELECT upper_bound,last_at,last_book,last_event FROM " + owner.table + "cursor " + """
        WHERE id=? AND guardian_key=? AND (target_key=? OR target_key IS NULL)
          AND member_id=? AND scope=? AND grant_version=? AND expires_at>clock_timestamp()
        """, rs -> {
      if (!rs.next()) throw error(400);
      return new Position(rs.getObject(1, OffsetDateTime.class), rs.getObject(2, OffsetDateTime.class),
          rs.getObject(3, UUID.class), rs.getObject(4, UUID.class));
    }, id, actor, target, member, scope.name(), version);
  }

  private UUID save(String actor, String target, UUID member, SupervisionScope scope, long version,
      Position position, Row last, Instant validUntil) {
    UUID id = UUID.randomUUID();
    Instant expiry = Instant.now().plusSeconds(600);
    db.update("INSERT INTO " + owner.table + "cursor " + """
        (id,guardian_key,target_key,member_id,scope,grant_version,upper_bound,last_at,last_book,last_event,expires_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """, id, actor, target, member, scope.name(), version, position.upper(), last.time(), last.key(), last.tie(),
        utc(expiry.isBefore(validUntil) ? expiry : validUntil));
    return id;
  }

  public void prune() {
    db.update("DELETE FROM " + owner.table + "cursor WHERE expires_at<=clock_timestamp()");
    db.update("DELETE FROM " + owner.table + "audit WHERE accessed_at<clock_timestamp()-interval '180 days'");
  }

  public static OffsetDateTime utc(Instant time) { return time.atOffset(ZoneOffset.UTC); }
  private static ResponseStatusException error(int status) { return new ResponseStatusException(HttpStatus.valueOf(status)); }
}
