package com.bookrush.readerstateservice.api;

import java.sql.Date;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Private daily reading projection. All changes for a subject are serialized on its row. */
@Service
public class DailyPlantService {
  static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final Clock clock;

  @Autowired
  public DailyPlantService(JdbcTemplate jdbc) { this.jdbc = jdbc; this.clock = Clock.systemUTC(); }

  DailyPlantService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

  @Transactional
  public Map<String, Object> get(String subject) {
    LocalDate today = LocalDate.now(clock.withZone(ZONE));
    var state = lock(subject, today);
    return advance(subject, state, today);
  }

  @Transactional
  public Map<String, Object> record(String subject, UUID eventId, int seconds, Instant endedAt) {
    Instant now = clock.instant();
    if (eventId == null || endedAt == null || seconds < 1 || seconds > 60
        || endedAt.isAfter(now.plusSeconds(30)) || endedAt.isBefore(now.minusSeconds(300))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid reading interval");
    }
    LocalDate today = now.atZone(ZONE).toLocalDate();
    var state = lock(subject, today);
    int inserted = jdbc.update("INSERT INTO reader_state.plant_reading_event(subject_key,event_id) VALUES (?,?) ON CONFLICT DO NOTHING", subject, eventId);
    if (inserted != 0) {
      Instant intervalEnd = endedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
      Instant start = intervalEnd.minusSeconds(seconds);
      while (start.isBefore(intervalEnd)) {
        LocalDate day = start.atZone(ZONE).toLocalDate();
        Instant boundary = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant end = intervalEnd.isBefore(boundary) ? intervalEnd : boundary;
        long portion = java.time.Duration.between(start, end).getSeconds();
        if (portion > 0 && !day.isBefore(state.day())) {
          jdbc.update("INSERT INTO reader_state.plant_reading_day(subject_key,day,active_seconds) VALUES (?,?,?) ON CONFLICT(subject_key,day) DO UPDATE SET active_seconds=reader_state.plant_reading_day.active_seconds+EXCLUDED.active_seconds", subject, day, (int) portion);
        }
        start = end;
      }
    }
    return advance(subject, state, today);
  }

  private record Projection(int stage, boolean sad, LocalDate day, boolean applied, int streak) {}

  private Projection lock(String subject, LocalDate today) {
    jdbc.update("INSERT INTO reader_state.daily_plant(subject_key,start_day) VALUES (?,?) ON CONFLICT DO NOTHING", subject, today);
    return jdbc.queryForObject("SELECT stage,sad,start_day,goal_applied,streak_days FROM reader_state.daily_plant WHERE subject_key=? FOR UPDATE", (rs, row) -> new Projection(rs.getInt(1), rs.getBoolean(2), rs.getDate(3).toLocalDate(), rs.getBoolean(4), rs.getInt(5)), subject);
  }

  private Map<String, Object> advance(String subject, Projection projection, LocalDate today) {
    PlantGrowth.State plant = new PlantGrowth.State(0, false);
    var days = new java.util.HashMap<LocalDate, Integer>();
    jdbc.query("SELECT day,active_seconds FROM reader_state.plant_reading_day WHERE subject_key=? AND day>=? AND day<=?", rs -> { days.put(rs.getDate(1).toLocalDate(), rs.getInt(2)); }, subject, projection.day(), today);
    LocalDate day = projection.day();
    int streak = 0;
    boolean applied = false;
    while (!day.isAfter(today)) {
      applied = days.getOrDefault(day, 0) >= PlantGrowth.DAILY_GOAL_SECONDS;
      if (applied) { plant = PlantGrowth.readingGoalReached(plant); streak++; }
      else if (day.isBefore(today)) { plant = PlantGrowth.missedDay(plant); streak = 0; }
      day = day.plusDays(1);
    }
    int todaySeconds = days.getOrDefault(today, 0);
    jdbc.update("UPDATE reader_state.daily_plant SET stage=?,sad=?,goal_applied=?,streak_days=? WHERE subject_key=?", plant.stage(), plant.sad(), applied, streak, subject);
    Long total = jdbc.queryForObject("SELECT COALESCE(SUM(active_seconds),0) FROM reader_state.plant_reading_day WHERE subject_key=?", Long.class, subject);
    return Map.of("formulaVersion", "daily-reading-v2", "stage", plant.stage(), "sad", plant.sad(), "streakDays", streak, "todaySeconds", todaySeconds, "goalSeconds", PlantGrowth.DAILY_GOAL_SECONDS, "goalReached", applied, "totalReadingMinutes", total == null ? 0 : total / 60, "measuredUntil", today);
  }
}
