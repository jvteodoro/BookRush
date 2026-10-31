package com.bookrush.readerstateservice.api;

/** State transitions for the daily reading plant. */
public final class PlantGrowth {
  public static final int MAX_STAGE = 5;
  public static final int DAILY_GOAL_SECONDS = 15 * 60;

  private PlantGrowth() {}

  public record State(int stage, boolean sad) {
    public State {
      if (stage < 0 || stage > MAX_STAGE || (stage == 0 && sad)) {
        throw new IllegalArgumentException("invalid plant state");
      }
    }
  }

  public static State readingGoalReached(State state) {
    if (state.sad()) return new State(state.stage(), false);
    return new State(Math.min(MAX_STAGE, state.stage() + 1), false);
  }

  public static State missedDay(State state) {
    if (state.stage() == 0) return state;
    if (state.sad()) return new State(state.stage() - 1, false);
    return new State(state.stage(), true);
  }
}
