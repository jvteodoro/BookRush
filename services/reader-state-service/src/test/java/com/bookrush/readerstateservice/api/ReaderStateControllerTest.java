package com.bookrush.readerstateservice.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.bookrush.readerstateservice.BehaviorEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ReaderStateControllerTest {
  private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
  private final BehaviorEventPublisher behavior = org.mockito.Mockito.mock(BehaviorEventPublisher.class);
  private final DailyPlantService dailyPlant = org.mockito.Mockito.mock(DailyPlantService.class);
  private final ReaderStateController controller = new ReaderStateController(jdbc, behavior, dailyPlant);
  private final Principal user = () -> "iss:subject-1";

  @Test
  void progressPersistsPreciseLocatorForTheAuthenticatedReader() {
    UUID book = UUID.randomUUID();
    Map<String, Object> locator = Map.of("href", "/versions/v1/chapter", "type", "text/html",
        "locations", Map.of("progression", 0.432123));
    controller.progress(book, new ReaderStateController.Progress(0L, 43.2123, locator), user, null);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains("locator = EXCLUDED.locator"),
        eq(user.getName()), eq(book), eq(0L), eq(43.2123), org.mockito.ArgumentMatchers.contains("0.432123"));
    controller.getProgress(book, user);
    verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("locator::text AS locator"), eq(user.getName()), eq(book));
    assertThrows(ResponseStatusException.class, () -> controller.getProgress(book, null));
    assertThrows(ResponseStatusException.class, () -> controller.progress(book,
        new ReaderStateController.Progress(0L, 43.2123, locator), null, null));
  }

  @Test
  void progressRejectsMalformedLocatorsAndOutOfRangePercent() {
    UUID book = UUID.randomUUID();
    assertThrows(ResponseStatusException.class, () -> controller.progress(book,
        new ReaderStateController.Progress(0L, 20d, Map.of("href", "x")), user, null));
    for (double percent : new double[] { -1, 101, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(ResponseStatusException.class, () -> controller.progress(book,
          new ReaderStateController.Progress(0L, percent), user, null));
    }
  }

  @Test
  void bookmarkReturnsPersistedIdentityAndLocatorForRepeatedWrites() {
    UUID book = UUID.randomUUID();
    UUID persistedId = UUID.randomUUID();
    var locator = Map.of("href", "/versions/v1/chapters/c1/content", "type", "text/html",
        "locations", Map.of("progression", 0.4321));
    when(jdbc.queryForMap(anyString(), any(Object[].class)))
        .thenReturn(Map.of("id", persistedId));
    var first = controller.bookmark(book, Map.of("locator", locator), user);
    var second = controller.bookmark(book, Map.of("locator", locator), user);
    org.junit.jupiter.api.Assertions.assertEquals(persistedId, first.get("id"));
    org.junit.jupiter.api.Assertions.assertEquals(first, second);
    verify(jdbc, org.mockito.Mockito.times(2)).queryForMap(
        org.mockito.ArgumentMatchers.contains("md5(locator::text)) WHERE locator IS NOT NULL"),
        any(UUID.class), eq("iss:subject-1"), eq(book), eq(0L), eq(""),
        org.mockito.ArgumentMatchers.contains("0.4321"), eq(null), eq(null));
  }

  @Test
  void bookmarkRenameAndDeleteAreScopedToTheReaderAndBook() {
    UUID book = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", id, "name", "Trecho")));
    var result = controller.renameBookmark(book, id, Map.of("name", "  Trecho  "), user);
    org.junit.jupiter.api.Assertions.assertEquals("Trecho", result.get("name"));
    verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("WHERE id = ? AND subject_key = ? AND book_id = ?"),
        eq("Trecho"), eq(id), eq(user.getName()), eq(book));
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    controller.deleteBookmark(book, id, user);
    verify(jdbc).update(eq("DELETE FROM reader_state.bookmarks WHERE id = ? AND subject_key = ? AND book_id = ?"),
        eq(id), eq(user.getName()), eq(book));
    assertThrows(ResponseStatusException.class, () -> controller.renameBookmark(book, id, Map.of("name", "Trecho"), null));
    assertThrows(ResponseStatusException.class, () -> controller.deleteBookmark(book, id, null));
  }

  @Test
  void bookmarkNamesAreRequiredForRenameAndMissingBookmarksReturnNotFound() {
    UUID book = UUID.randomUUID(); UUID id = UUID.randomUUID();
    for (String name : List.of("", "   ", "x".repeat(121))) {
      assertThrows(ResponseStatusException.class, () -> controller.renameBookmark(book, id, Map.of("name", name), user));
      assertThrows(ResponseStatusException.class, () -> controller.bookmark(book, Map.of("name", name, "positionCodepoint", 0), user));
    }
    assertThrows(ResponseStatusException.class, () -> controller.renameBookmark(book, id, Map.of(), user));
    var missing = assertThrows(ResponseStatusException.class, () -> controller.renameBookmark(book, id, Map.of("name", "Trecho"), user));
    org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, missing.getStatusCode());
    var deleted = assertThrows(ResponseStatusException.class, () -> controller.deleteBookmark(book, id, user));
    org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, deleted.getStatusCode());
  }

  @Test
  void bookmarksRejectMissingIdentityAndMalformedLocators() {
    UUID book = UUID.randomUUID();
    assertThrows(ResponseStatusException.class, () -> controller.bookmark(book, Map.of(), user));
    assertThrows(ResponseStatusException.class, () -> controller.bookmark(book,
        Map.of("locator", Map.of("href", "x", "type", "text/html", "locations", Map.of("progression", 2))), user));
    assertThrows(ResponseStatusException.class, () -> controller.bookmark(book,
        Map.of("locator", Map.of()), user));
    assertThrows(ResponseStatusException.class, () -> controller.bookmark(book, Map.of(), null));
    assertThrows(ResponseStatusException.class, () -> controller.bookmarks(book, null));
  }

  @Test
  void bookmarkListIsScopedToReaderAndIncludesLocator() {
    UUID book = UUID.randomUUID();
    controller.bookmarks(book, user);
    verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("locator::text AS locator"),
        eq("iss:subject-1"), eq(book));
  }

  @Test
  void openedUsesValidTwoParameterUpsert() {
    UUID bookId = UUID.randomUUID();
    controller.opened(bookId, user, null);
    verify(jdbc).update(
        eq("insert into reader_state.recent(subject_key,book_id) values(?,?) on conflict(subject_key,book_id) do update set last_opened_at=now()"),
        eq("iss:subject-1"), eq(bookId));
  }

  @Test
  void stateCannotBeWrittenWithoutAnAuthenticatedPrincipal() {
    assertThrows(ResponseStatusException.class, () -> controller.recent(null));
  }

  @Test
  void plantUsesTheAuthenticatedSubject() {
    when(dailyPlant.get("iss:subject-1")).thenReturn(Map.of("stage", 2, "sad", false));
    org.junit.jupiter.api.Assertions.assertEquals(2, controller.plant(user).get("stage"));
    verify(dailyPlant).get("iss:subject-1");
    assertThrows(ResponseStatusException.class, () -> controller.plant(null));
  }

  @Test
  void readingIntervalIsScopedToTheAuthenticatedReader() {
    var id = UUID.randomUUID();
    var end = java.time.Instant.now();
    var interval = new ReaderStateController.ReadingInterval(id, 15, end);
    when(dailyPlant.record("iss:subject-1", id, 15, end)).thenReturn(Map.of("stage", 0));
    controller.readingTime(interval, user);
    verify(dailyPlant).record("iss:subject-1", id, 15, end);
    assertThrows(ResponseStatusException.class, () -> controller.readingTime(interval, null));
  }
  @Test
  void sessionStartPersistsBookAndFeedCorrelation() {
    UUID bookId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID impressionId = UUID.randomUUID();
    var request = new MockHttpServletRequest();
    request.addHeader("X-BookRush-Recommendation-Item", itemId.toString());
    request.addHeader("X-BookRush-Impression", impressionId.toString());

    controller.startSession(Map.of("bookId", bookId.toString()), user, request);

    verify(jdbc).update(
        eq("INSERT INTO reader_state.session(id, subject_key, book_id, recommendation_item_id, impression_id, started_at) VALUES (?, ?, ?, ?, ?, now())"),
        any(UUID.class), eq("iss:subject-1"), eq(bookId), eq(itemId), eq(impressionId));
  }

  @Test
  void directSessionDoesNotInventFeedAttribution() {
    UUID bookId = UUID.randomUUID();

    controller.startSession(Map.of("bookId", bookId.toString()), user,
        new MockHttpServletRequest());

    verify(jdbc).update(
        eq("INSERT INTO reader_state.session(id, subject_key, book_id, recommendation_item_id, impression_id, started_at) "
            + "VALUES (?, ?, ?, ?, ?, now())"),
        any(UUID.class), eq("iss:subject-1"), eq(bookId), eq(null), eq(null));
  }

  @Test
  void endingSessionPublishesSessionEventUsingPersistedOrigin() {
    UUID sessionId = UUID.randomUUID();
    UUID bookId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID impressionId = UUID.randomUUID();
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(sessionId), eq("iss:subject-1")))
        .thenReturn(Map.of("bookId", bookId, "recommendationItemId", itemId,
            "impressionId", impressionId));
    when(jdbc.update(eq("UPDATE reader_state.session SET ended_at = now(), active_seconds = ? "
        + "WHERE id = ? AND subject_key = ? AND ended_at IS NULL"),
        eq(120), eq(sessionId), eq("iss:subject-1"))).thenReturn(1);
    when(dailyPlant.get("iss:subject-1")).thenReturn(Map.of("stage", 1));

    controller.endSession(sessionId, Map.of("activeSeconds", 120), user,
        new MockHttpServletRequest());

    verify(behavior).publish(eq("READ_SESSION"), eq(bookId),
        eq(Map.of("recommendationItemId", itemId, "impressionId", impressionId,
            "activeSeconds", 120)), eq(null));
  }

  @Test
  void progressPublishesRecommendationCorrelationAndCompletionIdempotencyKey() {
    UUID bookId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID impressionId = UUID.randomUUID();
    var request = new MockHttpServletRequest();
    request.addHeader("X-BookRush-Recommendation-Item", itemId.toString());
    request.addHeader("X-BookRush-Impression", impressionId.toString());

    controller.progress(bookId, new ReaderStateController.Progress(100L, 100d), user, request);

    verify(behavior).publish(eq("READ_PROGRESS"), eq(bookId),
        eq(Map.of("recommendationItemId", itemId, "impressionId", impressionId, "percent", 100d)), any());
    verify(behavior).publishWithKey(eq("BOOK_COMPLETE"), eq(bookId),
        eq(Map.of("recommendationItemId", itemId, "impressionId", impressionId, "percent", 100)), any(),
        eq("BOOK_COMPLETE:iss:subject-1:" + bookId));
  }
  @Test
  void openingMarksOnlyTheAuthenticatedReadersBookAsSeen() {
    UUID book = UUID.randomUUID();
    controller.opened(book, user, null);
    verify(jdbc).update(
        eq("INSERT INTO reader_state.seen_book(subject_key,book_id) VALUES (?,?) "
            + "ON CONFLICT(subject_key,book_id) DO UPDATE SET last_seen_at=clock_timestamp()"),
        eq(user.getName()), eq(book));
    assertThrows(ResponseStatusException.class, () -> controller.opened(book, null, null));
  }

  @Test
  void seenRejectsUnauthenticatedAndUnboundedQueries() {
    assertThrows(ResponseStatusException.class, () -> controller.seen(List.of(UUID.randomUUID()), null));
    assertThrows(ResponseStatusException.class, () -> controller.seen(List.of(), user));
    assertThrows(ResponseStatusException.class,
        () -> controller.seen(java.util.Collections.nCopies(101, UUID.randomUUID()), user));
  }

}
