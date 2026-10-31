package com.bookrush.socialservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/social/books/{bookId}/ratings")
public class BookRatingsController {
  private final JdbcTemplate jdbc;
  private final RestClient catalog;

  public BookRatingsController(JdbcTemplate jdbc, RestClient.Builder builder,
      @Value("${CATALOG_URL:http://catalog-service:8080}") String catalogUrl) {
    this.jdbc = jdbc;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(3000);
    this.catalog = builder.requestFactory(factory).baseUrl(catalogUrl).build();
  }

  public record Rating(@NotNull @DecimalMin("1") @DecimalMax("5") BigDecimal stars) {}
  public record Summary(UUID bookId, long totalRatings, BigDecimal averageRating, Integer myRating) {}

  private String subject(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }
    return principal.getName();
  }

  private void requireBook(UUID bookId) {
    try {
      // Catalogue metadata is public; product tokens must not cross the platform realm.
      catalog.get().uri("/api/v1/books/{id}", bookId).retrieve().toBodilessEntity();
    } catch (RestClientResponseException error) {
      if (error.getStatusCode().value() == 404) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "book not found");
      }
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog unavailable");
    } catch (RestClientException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "catalog unavailable");
    }
  }

  private Summary summary(UUID bookId, String subject) {
    return jdbc.queryForObject("""
        select count(*) as total, round(avg(stars),2) as average,
          max(stars) filter (where subject_key=?) as mine
        from social.book_rating where book_id=?
        """, (row, index) -> new Summary(bookId, row.getLong("total"), row.getBigDecimal("average"),
            row.getObject("mine", Integer.class)), subject, bookId);
  }

  @GetMapping
  public Summary get(@PathVariable UUID bookId, Principal principal) {
    String subject = subject(principal);
    requireBook(bookId);
    return summary(bookId, subject);
  }

  @PutMapping
  @Transactional
  public Summary put(@PathVariable UUID bookId, @Valid @RequestBody Rating rating, Principal principal) {
    String subject = subject(principal);
    int stars;
    try {
      stars = rating.stars().intValueExact();
    } catch (ArithmeticException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stars must be an integer from 1 to 5");
    }
    requireBook(bookId);
    jdbc.update("""
        insert into social.book_rating(subject_key,book_id,stars) values(?,?,?)
        on conflict(subject_key,book_id) do update set stars=excluded.stars,updated_at=clock_timestamp()
        where book_rating.stars<>excluded.stars
        """, subject, bookId, stars);
    return summary(bookId, subject);
  }

  @DeleteMapping
  @Transactional
  public Summary delete(@PathVariable UUID bookId, Principal principal) {
    String subject = subject(principal);
    requireBook(bookId);
    jdbc.update("delete from social.book_rating where subject_key=? and book_id=?", subject, bookId);
    return summary(bookId, subject);
  }
}
