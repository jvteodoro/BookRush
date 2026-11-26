package com.bookrush.readerstateservice.api;

import com.bookrush.platform.security.ProductAccessAuthorization;
import com.bookrush.platform.security.ProductAccessAuthorization.SupervisionScope;
import com.bookrush.platform.security.SupervisedProjection;
import com.bookrush.platform.security.SupervisedProjection.Position;
import com.bookrush.platform.security.SupervisedProjection.Row;
import static com.bookrush.platform.security.SupervisedProjection.NIL;
import static com.bookrush.platform.security.SupervisedProjection.utc;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
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
@ConditionalOnProperty(name = "reader-state.supervision.enabled", havingValue = "true")
class SupervisedReadingController {
  enum Scope { BOOKS_ACCESSED, READING_ACTIVITY }
  record Book(UUID bookId, Instant accessedAt) {}
  record Day(LocalDate date, long minutes, long sessions) {}
  private final JdbcTemplate db;
  private final SupervisedProjection projection;

  SupervisedReadingController(JdbcTemplate db, ProductAccessAuthorization access, MeterRegistry metrics,
      @Value("${bookrush.security.product-access.jwk-set-uri}") String jwks,
      @Value("${bookrush.security.product-access.workload-issuer}") String issuer) {
    this.db = db;
    projection = new SupervisedProjection(db, access, metrics, SupervisedProjection.Owner.READER_STATE, jwks, issuer);
  }

  @GetMapping("/api/internal/v1/reader/supervision/{memberId}/history")
  ResponseEntity<SupervisedProjection.Page> history(@PathVariable UUID memberId, @RequestParam Scope scope,
      @RequestParam(defaultValue = "25") int limit, @RequestParam(required = false) UUID cursor,
      @RequestHeader(value = SupervisedProjection.WORKLOAD_HEADER, required = false) String credential,
      JwtAuthenticationToken guardian, HttpServletRequest request) {
    return projection.read(request, guardian, credential, memberId, SupervisionScope.valueOf(scope.name()), limit, cursor,
        (target, from, position, count) -> scope == Scope.READING_ACTIVITY
            ? days(target.toString(), from, position, count) : books(target.toString(), from, position, count));
  }

  private List<Row> books(String target, OffsetDateTime from, Position position, int limit) {
    return db.query("""
        SELECT occurred_at, book_id, request_id FROM reader_state.book_open
        WHERE subject_key=? AND occurred_at>=? AND occurred_at<?
          AND (occurred_at, book_id, request_id)<(?, ?, ?)
        ORDER BY occurred_at DESC, book_id DESC, request_id DESC LIMIT ?
        """, (rs, i) -> {
      var time = rs.getObject("occurred_at", OffsetDateTime.class);
      var book = rs.getObject("book_id", UUID.class);
      return new Row(time, book, rs.getObject("request_id", UUID.class), new Book(book, time.toInstant()));
    }, target, from, position.upper(), position.time(), position.key(), position.tie(), limit);
  }

  private List<Row> days(String target, OffsetDateTime from, Position position, int limit) {
    return db.query("""
        SELECT (ended_at AT TIME ZONE 'America/Sao_Paulo')::date AS day,
               SUM(active_seconds)/60 AS minutes, COUNT(*) AS sessions
        FROM reader_state.session
        WHERE subject_key=? AND active_seconds>0 AND started_at>=? AND ended_at<?
        GROUP BY day
        HAVING (((ended_at AT TIME ZONE 'America/Sao_Paulo')::date)::timestamp AT TIME ZONE 'America/Sao_Paulo') < ?
        ORDER BY day DESC LIMIT ?
        """,
        (rs, i) -> {
          var day = rs.getObject("day", LocalDate.class);
          return new Row(utc(day.atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant()),
              NIL, NIL, new Day(day, rs.getLong("minutes"), rs.getLong("sessions")));
        }, target, from, position.upper(), position.time(), limit);
  }

  @Scheduled(fixedDelay = 3_600_000)
  void prune() {
    projection.prune();
  }
}
